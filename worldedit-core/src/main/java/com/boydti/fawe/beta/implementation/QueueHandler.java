package com.boydti.fawe.beta.implementation;

import com.boydti.fawe.Fawe;
import com.boydti.fawe.FaweCache;
import com.boydti.fawe.beta.ChunkFilterBlock;
import com.boydti.fawe.beta.IChunk;
import com.boydti.fawe.beta.IChunkGet;
import com.boydti.fawe.beta.IChunkSet;
import com.boydti.fawe.beta.IQueueExtent;
import com.boydti.fawe.beta.Trimable;
import com.boydti.fawe.config.Settings;
import com.boydti.fawe.object.collection.IterableThreadLocal;
import com.boydti.fawe.util.MemUtil;
import com.boydti.fawe.util.TaskManager;
import com.boydti.fawe.wrappers.WorldWrapper;
import com.google.common.util.concurrent.Futures;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.world.World;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Supplier;

/**
 * Class which handles all the queues {@link IQueueExtent}
 */
public abstract class QueueHandler implements Trimable, Runnable {

    private ForkJoinPool forkJoinPoolPrimary = new ForkJoinPool();
    private ForkJoinPool forkJoinPoolSecondary = new ForkJoinPool();
    private ThreadPoolExecutor blockingExecutor = FaweCache.IMP.newBlockingExecutor();
    private ConcurrentLinkedQueue<FutureTask> syncTasks = new ConcurrentLinkedQueue<>();

    private Map<World, WeakReference<IChunkCache<IChunkGet>>> chunkCache = new HashMap<>();
    private IterableThreadLocal<IQueueExtent> queuePool = new IterableThreadLocal<>(QueueHandler.this::create);
    /**
     * Used to calculate elapsed time in milliseconds and ensure block placement doesn't lag the
     * server
     */
    private long last;
    private long allocate = 50;
    private double targetTPS = 18;

    public QueueHandler() {
        TaskManager.IMP.repeat(this, 1);
    }

    @Override
    public void run() {
        if (!Fawe.isMainThread()) {
            throw new IllegalStateException("Not main thread");
        }
        if (!syncTasks.isEmpty()) {
            long now = System.currentTimeMillis();
            targetTPS = 18 - Math.max(Settings.IMP.QUEUE.EXTRA_TIME_MS * 0.05, 0);
            long diff = 50 + this.last - (this.last = now);
            long absDiff = Math.abs(diff);
            if (diff == 0) {
                allocate = Math.min(50, allocate + 1);
            } else if (diff < 0) {
                allocate = Math.max(5, allocate + diff);
            } else if (!Fawe.get().getTimer().isAbove(targetTPS)) {
                allocate = Math.max(5, allocate - 1);
            }
            long currentAllocate = allocate - absDiff;

            if (!MemUtil.isMemoryFree()) {
                // TODO reduce mem usage
            }

            long taskAllocate = currentAllocate;
            boolean wait = false;
            do {
                Runnable task = syncTasks.poll();
                if (task == null) {
                    if (wait) {
                        synchronized (syncTasks) {
                            try {
                                syncTasks.wait(1);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        task = syncTasks.poll();
                        wait = false;
                    } else {
                        break;
                    }
                }
                if (task != null) {
                    task.run();
                    wait = true;
                }
            } while (System.currentTimeMillis() - now < taskAllocate);
        }
        while (!syncTasks.isEmpty()) {
            final FutureTask task = syncTasks.poll();
            if (task != null) {
                task.run();
            }
        }
    }

    public <T extends Future<T>> void complete(Future<T> task) {
        try {
            while (task != null) {
                task = task.get();
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    public <T> Future<T> async(Runnable run, T value) {
        return forkJoinPoolSecondary.submit(run, value);
    }

    public Future<?> async(Runnable run) {
        return forkJoinPoolSecondary.submit(run);
    }

    public <T> Future<T> async(Callable<T> call) {
        return forkJoinPoolSecondary.submit(call);
    }

    public ForkJoinTask submit(Runnable call) {
        return forkJoinPoolPrimary.submit(call);
    }

    public <T> Future<T> sync(Runnable run, T value) {
        if (Fawe.isMainThread()) {
            run.run();
            return Futures.immediateFuture(value);
        }
        final FutureTask<T> result = new FutureTask<>(run, value);
        syncTasks.add(result);
        notifySync();
        return result;
    }

    public <T> Future<T> sync(Runnable run) {
        if (Fawe.isMainThread()) {
            run.run();
            return Futures.immediateCancelledFuture();
        }
        final FutureTask<T> result = new FutureTask<>(run, null);
        syncTasks.add(result);
        notifySync();
        return result;
    }

    public <T> Future<T> sync(Callable<T> call) throws Exception {
        if (Fawe.isMainThread()) {
            return Futures.immediateFuture(call.call());
        }
        final FutureTask<T> result = new FutureTask<>(call);
        syncTasks.add(result);
        notifySync();
        return result;
    }

    public <T> Future<T> sync(Supplier<T> call) {
        if (Fawe.isMainThread()) {
            return Futures.immediateFuture(call.get());
        }
        final FutureTask<T> result = new FutureTask<>(call::get);
        syncTasks.add(result);
        notifySync();
        return result;
    }

    private void notifySync() {
        synchronized (syncTasks) {
            syncTasks.notifyAll();
        }
    }

    public <T extends Future<T>> T submit(IChunk<T> chunk) {
//        if (MemUtil.isMemoryFree()) { TODO NOT IMPLEMENTED - optimize this
//            return (T) forkJoinPoolSecondary.submit(chunk);
//        }
        return (T) blockingExecutor.submit(chunk);
    }

    /**
     * Get or create the WorldChunkCache for a world
     *
     * @param world
     * @return
     */
    public IChunkCache<IChunkGet> getOrCreateWorldCache(World world) {
        world = WorldWrapper.unwrap(world);

        synchronized (chunkCache) {
            final WeakReference<IChunkCache<IChunkGet>> ref = chunkCache.get(world);
            if (ref != null) {
                final IChunkCache<IChunkGet> cached = ref.get();
                if (cached != null) {
                    return cached;
                }
            }
            final IChunkCache<IChunkGet> created = new ChunkCache<>(world);
            chunkCache.put(world, new WeakReference<>(created));
            return created;
        }
    }

    public IQueueExtent create() {
        return new SingleThreadQueueExtent();
    }

    public abstract void startSet(boolean parallel);

    public abstract void endSet(boolean parallel);

    public IQueueExtent getQueue(World world) {
        final IQueueExtent queue = queuePool.get();
        IChunkCache<IChunkGet> cacheGet = getOrCreateWorldCache(world);
        IChunkCache<IChunkSet> set = null; // TODO cache?
        queue.init(world, cacheGet, set);
        return queue;
    }

    @Override
    public boolean trim(boolean aggressive) {
        boolean result = true;
        synchronized (chunkCache) {
            final Iterator<Map.Entry<World, WeakReference<IChunkCache<IChunkGet>>>> iter = chunkCache
                .entrySet().iterator();
            while (iter.hasNext()) {
                final Map.Entry<World, WeakReference<IChunkCache<IChunkGet>>> entry = iter.next();
                final WeakReference<IChunkCache<IChunkGet>> value = entry.getValue();
                final IChunkCache<IChunkGet> cache = value.get();
                if (cache.trim(aggressive)) {
                    iter.remove();
                    continue;
                }
                result = false;
            }
        }
        return result;
    }
}
