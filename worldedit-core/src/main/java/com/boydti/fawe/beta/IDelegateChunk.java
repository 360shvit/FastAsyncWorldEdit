package com.boydti.fawe.beta;

import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockStateHolder;

/**
 * Delegate for IChunk
 * @param <T> The result type (typically returns true when the chunk is applied)
 * @param <V> The IQueue class
 * @param <U> parent class
 */
public interface IDelegateChunk<T, V extends IQueueExtent, U extends IChunk<T, V>> extends IChunk<T, V> {
    U getParent();

    default IChunk getRoot() {
        IChunk root = getParent();
        while (root instanceof IDelegateChunk) {
            root = ((IDelegateChunk) root).getParent();
        }
        return root;
    }

    @Override
    default boolean setBiome(final int x, final int y, final int z, final BiomeType biome) {
        return getParent().setBiome(x, y, z, biome);
    }

    @Override
    default boolean setBlock(final int x, final int y, final int z, final BlockStateHolder holder) {
        return getParent().setBlock(x, y, z, holder);
    }

    @Override
    default BiomeType getBiome(final int x, final int z) {
        return getParent().getBiome(x, z);
    }

    @Override
    default BlockState getBlock(final int x, final int y, final int z) {
        return getParent().getBlock(x, y, z);
    }

    @Override
    default BaseBlock getFullBlock(final int x, final int y, final int z) {
        return getParent().getFullBlock(x, y, z);
    }

    @Override
    default void init(final V extent, final int X, final int Z) {
        getParent().init(extent, X, Z);
    }

    @Override
    default int getX() {
        return getParent().getX();
    }

    @Override
    default int getZ() {
        return getParent().getZ();
    }


    @Override
    default boolean trim(boolean aggressive) {
        return getParent().trim(aggressive);
    }

    /**
     * Apply this chunk to the world
     * @return result T (typically a boolean)
     */
    @Override
    default T apply() {
        return getParent().apply();
    }

    @Override
    default boolean isEmpty() {
        return getParent().isEmpty();
    }

    default <T extends IChunk> T findParent(final Class<T> clazz) {
        IChunk root = getParent();
        if (clazz.isAssignableFrom(root.getClass())) return (T) root;
        while (root instanceof IDelegateChunk) {
            root = ((IDelegateChunk) root).getParent();
            if (clazz.isAssignableFrom(root.getClass())) return (T) root;
        }
        return null;
    }

    @Override
    default void set(Filter filter) {
        getParent().set(filter);
    }
}
