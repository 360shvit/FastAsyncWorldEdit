package com.boydti.fawe.beta;

import com.boydti.fawe.beta.implementation.WorldChunkCache;

import java.util.concurrent.Future;

public interface IDelegateQueueExtent extends IQueueExtent {
    IQueueExtent getParent();

    @Override
    default void init(WorldChunkCache cache) {
        getParent().init(cache);
    }

    @Override
    default IChunk getCachedChunk(int X, int Z) {
        return getParent().getCachedChunk(X, Z);
    }

    @Override
    default <T> Future<T> submit(IChunk<T, ?> chunk) {
        return getParent().submit(chunk);
    }

    @Override
    default IChunk create(boolean full) {
        return getParent().create(full);
    }

    @Override
    default IChunk wrap(IChunk root) {
        return getParent().wrap(root);
    }

    @Override
    default void flush() {
        getParent().flush();
    }

    @Override
    default boolean trim(boolean aggressive) {
        return getParent().trim(aggressive);
    }
}
