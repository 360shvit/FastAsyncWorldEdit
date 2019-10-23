package com.boydti.fawe.beta;

import com.boydti.fawe.FaweCache;
import com.boydti.fawe.beta.implementation.EmptyBatchProcessor;
import com.boydti.fawe.beta.implementation.MultiBatchProcessor;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector3;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public interface IBatchProcessor {
    /**
     * Process a chunk that has been set
     * @param chunk
     * @param get
     * @param set
     * @return
     */
    IChunkSet processBatch(IChunk chunk, IChunkGet get, IChunkSet set);

    /**
     * Convert this processor into an Extent based processor instead of a queue batch based on
     * @param child
     * @return
     */
    Extent construct(Extent child);

    /**
     * Utility method to trim a chunk based on min and max Y
     * @param set
     * @param minY
     * @param maxY
     * @return false if chunk is empty of blocks
     */
    default boolean trimY(IChunkSet set, int minY, int maxY) {
        int minLayer = (minY - 1) >> 4;
        for (int layer = 0; layer <= minLayer; layer++) {
            if (set.hasSection(layer)) {
                if (layer == minLayer) {
                    char[] arr = set.getArray(layer);
                    int index = (minY & 15) << 12;
                    for (int i = 0; i < index; i++) arr[i] = 0;
                    set.setBlocks(layer, arr);
                } else {
                    set.setBlocks(layer, null);
                }
            }
        }
        int maxLayer = (maxY + 1) >> 4;
        for (int layer = maxLayer; layer < FaweCache.IMP.CHUNK_LAYERS; layer++) {
            if (set.hasSection(layer)) {
                if (layer == minLayer) {
                    char[] arr = set.getArray(layer);
                    int index = ((maxY + 1) & 15) << 12;
                    for (int i = index; i < arr.length; i++) arr[i] = 0;
                    set.setBlocks(layer, arr);
                } else {
                    set.setBlocks(layer, null);
                }
            }
        }
        for (int layer = (minY - 15) >> 4; layer < (maxY + 15) >> 4; layer++) {
            if (set.hasSection(layer)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Utility method to trim entity and blocks with a provided contains function
     * @param set
     * @param contains
     * @return false if chunk is empty of NBT
     */
    default boolean trimNBT(IChunkSet set, Function<BlockVector3, Boolean> contains) {
        Set<CompoundTag> ents = set.getEntities();
        if (!ents.isEmpty()) {
            for (Iterator<CompoundTag> iter = ents.iterator(); iter.hasNext();) {
                CompoundTag ent = iter.next();
                if (!contains.apply(ent.getEntityPosition().toBlockPoint())) {
                    iter.remove();
                }
            }
        }
        Map<BlockVector3, CompoundTag> tiles = set.getTiles();
        if (!tiles.isEmpty()) {
            for (Iterator<Map.Entry<BlockVector3, CompoundTag>> iter = tiles.entrySet().iterator(); iter.hasNext();) {
                if (!contains.apply(iter.next().getKey())) {
                    iter.remove();
                }
            }
        }
        return !tiles.isEmpty() || !ents.isEmpty();
    }

    /**
     * Join two processors and return the result
     * @param other
     * @return
     */
    default IBatchProcessor join(IBatchProcessor other) {
        return MultiBatchProcessor.of(this, other);
    }

    /**
     * Return a new processor after removing all are instances of a specified class
     * @param clazz
     * @param <T>
     * @return
     */
    default <T extends IBatchProcessor> IBatchProcessor remove(Class<T> clazz) {
        if (clazz.isInstance(this)) {
            return EmptyBatchProcessor.INSTANCE;
        }
        return this;
    }
}
