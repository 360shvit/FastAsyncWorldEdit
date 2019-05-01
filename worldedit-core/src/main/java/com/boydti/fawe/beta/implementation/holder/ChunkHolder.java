package com.boydti.fawe.beta.implementation.holder;

import com.boydti.fawe.beta.Filter;
import com.boydti.fawe.beta.FilterBlock;
import com.boydti.fawe.beta.IQueueExtent;
import com.boydti.fawe.beta.implementation.blocks.CharSetBlocks;
import com.boydti.fawe.beta.IChunk;
import com.boydti.fawe.beta.IGetBlocks;
import com.boydti.fawe.beta.ISetBlocks;
import com.boydti.fawe.beta.implementation.SingleThreadQueueExtent;
import com.boydti.fawe.beta.implementation.WorldChunkCache;
import com.boydti.fawe.util.MathMan;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockStateHolder;

import java.util.function.Supplier;

/**
 * Abstract IChunk class that implements basic get/set blocks
 */
public abstract class ChunkHolder implements IChunk, Supplier<IGetBlocks> {
    private IGetBlocks get;
    private ISetBlocks set;
    private IBlockDelegate delegate;
    private IQueueExtent extent;
    private int X,Z;

    public ChunkHolder() {
        this.delegate = NULL;
    }

    public ChunkHolder(IBlockDelegate delegate) {
        this.delegate = delegate;
    }

    @Override
    public void filter(Filter filter, FilterBlock block) {
        block.init(X, Z, get);
        IGetBlocks get = getOrCreateGet();
        get.filter(filter, block);
    }

    @Override
    public boolean trim(boolean aggressive) {
        if (set != null) {
            boolean result = set.trim(aggressive);
            if (result) {
                delegate = NULL;
                get = null;
                set = null;
                return true;
            }
        }
        if (aggressive) {
            get = null;
            if (delegate == BOTH) {
                delegate = SET;
            } else if (delegate == GET) {
                delegate = NULL;
            }
        } else {
            get.trim(false);
        }
        return false;
    }

    @Override
    public boolean isEmpty() {
        return set == null || set.isEmpty();
    }

    public final IGetBlocks getOrCreateGet() {
        if (get == null) get = newGet();
        return get;
    }

    public final ISetBlocks getOrCreateSet() {
        if (set == null) set = set();
        return set;
    }

    public ISetBlocks set() {
        return new CharSetBlocks();
    }

    private IGetBlocks newGet() {
        if (extent instanceof SingleThreadQueueExtent) {
            WorldChunkCache cache = ((SingleThreadQueueExtent) extent).getCache();
            return cache.get(MathMan.pairInt(X, Z), this);
        }
        return get();
    }

    @Override
    public void optimize() {
        if (set != null) {
            set.optimize();
        }
    }

    @Override
    public void init(IQueueExtent extent, final int X, final int Z) {
        this.extent = extent;
        this.X = X;
        this.Z = Z;
        if (set != null) {
            set.reset();
            delegate = SET;
        } else {
            delegate = NULL;
        }
        get = null;
    }

    public IQueueExtent getExtent() {
        return extent;
    }

    @Override
    public int getX() {
        return X;
    }

    @Override
    public int getZ() {
        return Z;
    }

    @Override
    public boolean setBiome(final int x, final int y, final int z, final BiomeType biome) {
        return delegate.setBiome(this, x, y, z, biome);
    }

    @Override
    public boolean setBlock(final int x, final int y, final int z, final BlockStateHolder block) {
        return delegate.setBlock(this, x, y, z, block);
    }

    @Override
    public BiomeType getBiome(final int x, final int z) {
        return delegate.getBiome(this, x, z);
    }

    @Override
    public BlockState getBlock(final int x, final int y, final int z) {
        return delegate.getBlock(this, x, y, z);
    }

    @Override
    public BaseBlock getFullBlock(final int x, final int y, final int z) {
        return delegate.getFullBlock(this, x, y, z);
    }

    public interface IBlockDelegate {
        boolean setBiome(final ChunkHolder chunk, final int x, final int y, final int z, final BiomeType biome);

        boolean setBlock(final ChunkHolder chunk, final int x, final int y, final int z, final BlockStateHolder holder);

        BiomeType getBiome(final ChunkHolder chunk, final int x, final int z);

        BlockState getBlock(final ChunkHolder chunk, final int x, final int y, final int z);

        BaseBlock getFullBlock(final ChunkHolder chunk, final int x, final int y, final int z);
    }

    public static final IBlockDelegate NULL = new IBlockDelegate() {
        @Override
        public boolean setBiome(final ChunkHolder chunk, final int x, final int y, final int z, final BiomeType biome) {
            chunk.getOrCreateSet();
            chunk.delegate = SET;
            return chunk.setBiome(x, y, z, biome);
        }

        @Override
        public boolean setBlock(final ChunkHolder chunk, final int x, final int y, final int z, final BlockStateHolder block) {
            chunk.getOrCreateSet();
            chunk.delegate = SET;
            return chunk.setBlock(x, y, z, block);
        }

        @Override
        public BiomeType getBiome(final ChunkHolder chunk, final int x, final int z) {
            chunk.getOrCreateGet();
            chunk.delegate = GET;
            return chunk.getBiome(x, z);
        }

        @Override
        public BlockState getBlock(final ChunkHolder chunk, final int x, final int y, final int z) {
            chunk.getOrCreateGet();
            chunk.delegate = GET;
            return chunk.getBlock(x, y, z);
        }

        @Override
        public BaseBlock getFullBlock(final ChunkHolder chunk, final int x, final int y, final int z) {
            chunk.getOrCreateGet();
            chunk.delegate = GET;
            return chunk.getFullBlock(x, y, z);
        }
    };

    public static final IBlockDelegate GET = new IBlockDelegate() {
        @Override
        public boolean setBiome(final ChunkHolder chunk, final int x, final int y, final int z, final BiomeType biome) {
            chunk.getOrCreateSet();
            chunk.delegate = BOTH;
            return chunk.setBiome(x, y, z, biome);
        }

        @Override
        public boolean setBlock(final ChunkHolder chunk, final int x, final int y, final int z, final BlockStateHolder block) {
            chunk.getOrCreateSet();
            chunk.delegate = BOTH;
            return chunk.setBlock(x, y, z, block);
        }

        @Override
        public BiomeType getBiome(final ChunkHolder chunk, final int x, final int z) {
            return chunk.get.getBiome(x, z);
        }

        @Override
        public BlockState getBlock(final ChunkHolder chunk, final int x, final int y, final int z) {
            return chunk.get.getBlock(x, y, z);
        }

        @Override
        public BaseBlock getFullBlock(final ChunkHolder chunk, final int x, final int y, final int z) {
            return chunk.get.getFullBlock(x, y, z);
        }
    };

    public static final IBlockDelegate SET = new IBlockDelegate() {
        @Override
        public boolean setBiome(final ChunkHolder chunk, final int x, final int y, final int z, final BiomeType biome) {
            return chunk.set.setBiome(x, y, z, biome);
        }

        @Override
        public boolean setBlock(final ChunkHolder chunk, final int x, final int y, final int z, final BlockStateHolder block) {
            return chunk.set.setBlock(x, y, z, block);
        }

        @Override
        public BiomeType getBiome(final ChunkHolder chunk, final int x, final int z) {
            chunk.getOrCreateGet();
            chunk.delegate = BOTH;
            return chunk.getBiome(x, z);
        }

        @Override
        public BlockState getBlock(final ChunkHolder chunk, final int x, final int y, final int z) {
            chunk.getOrCreateGet();
            chunk.delegate = BOTH;
            return chunk.getBlock(x, y, z);
        }

        @Override
        public BaseBlock getFullBlock(final ChunkHolder chunk, final int x, final int y, final int z) {
            chunk.getOrCreateGet();
            chunk.delegate = BOTH;
            return chunk.getFullBlock(x, y, z);
        }
    };

    public static final IBlockDelegate BOTH = new IBlockDelegate() {
        @Override
        public boolean setBiome(final ChunkHolder chunk, final int x, final int y, final int z, final BiomeType biome) {
            return chunk.set.setBiome(x, y, z, biome);
        }

        @Override
        public boolean setBlock(final ChunkHolder chunk, final int x, final int y, final int z, final BlockStateHolder block) {
            return chunk.set.setBlock(x, y, z, block);
        }

        @Override
        public BiomeType getBiome(final ChunkHolder chunk, final int x, final int z) {
            return chunk.get.getBiome(x, z);
        }

        @Override
        public BlockState getBlock(final ChunkHolder chunk, final int x, final int y, final int z) {
            return chunk.get.getBlock(x, y, z);
        }

        @Override
        public BaseBlock getFullBlock(final ChunkHolder chunk, final int x, final int y, final int z) {
            return chunk.get.getFullBlock(x, y, z);
        }
    };
}
