package com.boydti.fawe.beta.implementation;

import com.boydti.fawe.FaweCache;
import com.boydti.fawe.beta.Filter;
import com.boydti.fawe.beta.FilterBlock;
import com.boydti.fawe.beta.IChunk;
import com.boydti.fawe.beta.IQueueExtent;
import com.boydti.fawe.beta.Trimable;
import com.boydti.fawe.config.Settings;
import com.boydti.fawe.object.collection.IterableThreadLocal;
import com.boydti.fawe.util.MemUtil;
import com.boydti.fawe.wrappers.WorldWrapper;
import com.google.common.util.concurrent.Futures;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.World;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Class which handles all the queues {@link IQueueExtent}
 */
public abstract class QueueHandler implements Trimable {
    private ForkJoinPool forkJoinPoolPrimary = new ForkJoinPool();
    private ForkJoinPool forkJoinPoolSecondary = new ForkJoinPool();
    private ThreadPoolExecutor blockingExecutor = FaweCache.newBlockingExecutor();
    private ConcurrentLinkedQueue<Runnable> syncTasks = new ConcurrentLinkedQueue();

    private Map<World, WeakReference<WorldChunkCache>> chunkCache = new HashMap<>();
    private IterableThreadLocal<IQueueExtent> queuePool = new IterableThreadLocal<IQueueExtent>() {
        @Override
        public IQueueExtent init() {
            return create();
        }
    };

    public <T extends Future<T>> T submit(IChunk<T> chunk) {
        if (MemUtil.isMemoryFree()) {
//            return (T) forkJoinPoolSecondary.submit(chunk);
        }
        return (T) blockingExecutor.submit(chunk);

    }

    /**
     * Get or create the WorldChunkCache for a world
     * @param world
     * @return
     */
    public WorldChunkCache getOrCreate(World world) {
        world = WorldWrapper.unwrap(world);

        synchronized (chunkCache) {
            final WeakReference<WorldChunkCache> ref = chunkCache.get(world);
            if (ref != null) {
                final WorldChunkCache cached = ref.get();
                if (cached != null) {
                    return cached;
                }
            }
            final WorldChunkCache created = new WorldChunkCache(world);
            chunkCache.put(world, new WeakReference<>(created));
            return created;
        }
    }

    public abstract IQueueExtent create();

    public IQueueExtent getQueue(World world) {
        IQueueExtent queue = queuePool.get();
        queue.init(getOrCreate(world));
        return queue;
    }

    @Override
    public boolean trim(final boolean aggressive) {
        boolean result = true;
        synchronized (chunkCache) {
            final Iterator<Map.Entry<World, WeakReference<WorldChunkCache>>> iter = chunkCache.entrySet().iterator();
            while (iter.hasNext()) {
                final Map.Entry<World, WeakReference<WorldChunkCache>> entry = iter.next();
                final WeakReference<WorldChunkCache> value = entry.getValue();
                final WorldChunkCache cache = value.get();
                if (cache == null || cache.size() == 0 || cache.trim(aggressive)) {
                    iter.remove();
                    continue;
                }
                result = false;
            }
        }
        return result;
    }

    public void apply(final World world, final Region region, final Filter filter) {
        // The chunks positions to iterate over
        final Set<BlockVector2> chunks = region.getChunks();
        final Iterator<BlockVector2> chunksIter = chunks.iterator();

        // Get a pool, to operate on the chunks in parallel
        final int size = Math.min(chunks.size(), Settings.IMP.QUEUE.PARALLEL_THREADS);
        ForkJoinTask[] tasks = new ForkJoinTask[size];
        for (int i = 0; i < size; i++) {
            tasks[i] = forkJoinPoolPrimary.submit(new Runnable() {
                @Override
                public void run() {
                    Filter newFilter = filter.fork();
                    // Create a chunk that we will reuse/reset for each operation
                    IQueueExtent queue = getQueue(world);
                    synchronized (queue) {
                        FilterBlock block = null;

                        while (true) {
                            // Get the next chunk pos
                            final BlockVector2 pos;
                            synchronized (chunksIter) {
                                if (!chunksIter.hasNext()) break;
                                pos = chunksIter.next();
                            }
                            final int X = pos.getX();
                            final int Z = pos.getZ();
                            IChunk chunk = queue.getCachedChunk(X, Z);
                            // Initialize
                            chunk.init(queue, X, Z);
                            try {
                                if (!newFilter.appliesChunk(X, Z)) {
                                    continue;
                                }
                                chunk = newFilter.applyChunk(chunk);

                                if (chunk == null) continue;

                                if (block == null) block = queue.initFilterBlock();
                                chunk.filter(newFilter, block);

                                newFilter.finishChunk(chunk);

                                queue.submit(chunk);
                            } finally {
                                if (filter != newFilter) {
                                    synchronized (filter) {
                                        newFilter.join(filter);
                                    }
                                }
                            }
                        }
                        queue.flush();
                    }
                }
            });
        }
        // Join filters
        for (int i = 0; i < tasks.length; i++) {
            ForkJoinTask task = tasks[i];
            if (task != null) {
                task.quietlyJoin();
            }
        }
    }
}