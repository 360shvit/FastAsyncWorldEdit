package com.boydti.fawe.object.function.block;

import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.function.RegionFunction;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.MutableBlockVector2D;

public class BiomeCopy implements RegionFunction {
    protected final Extent source;
    protected final Extent destination;
    private final MutableBlockVector2D mPos2d;

    public BiomeCopy(Extent source, Extent destination) {
        this.source = source;
        this.destination = destination;
        this.mPos2d = new MutableBlockVector2D();
        this.mPos2d.setComponents(Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    @Override
    public boolean apply(BlockVector3 position) throws WorldEditException {
        int x = position.getBlockX();
        int z = position.getBlockZ();
        if (x != mPos2d.getBlockX() || z != mPos2d.getBlockZ()) {
            mPos2d.setComponents(x, z);
            return destination.setBiome(mPos2d, source.getBiome(mPos2d));
        }
        return false;
    }
}