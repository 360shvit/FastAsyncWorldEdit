package com.boydti.fawe.object.mask;

import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.function.mask.AbstractExtentMask;
import com.sk89q.worldedit.math.BlockVector3;

public class OpacityMask extends AbstractExtentMask {

    private final int min, max;

    public OpacityMask(Extent extent, int min, int max) {
        super(extent);
        this.min = min;
        this.max = max;
    }

    @Override
    public boolean test(Extent extent, BlockVector3 vector) {
//        if (extent instanceof LightingExtent) {
//            int light = ((LightingExtent) extent).getOpacity(vector.getBlockX(), vector.getBlockY(), vector.getBlockZ());
//            return light >= min && light <= max;
//        }
        return false;
    }

}
