package com.sonicether.soundphysics.acoustics;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.IFluidBlock;

/**
 * Blocks that acoustically behave like solid matter even though they are
 * fluids. Vanilla ray tracing lets every liquid through (see
 * {@code BlockLiquid.canCollideCheck}), so lava and hot modded fluids such as
 * molten metals would otherwise neither occlude nor reflect sound.
 */
public final class AcousticMaterials {

    /**
     * Vanilla lava is 1300 K, and modded molten metals are typically between
     * 1000 K and 3000 K, while cold fluids (water, oil, ...) stay at 300 K.
     * Treating hot fluids as solid is what the maintainer settled on; keeping
     * the threshold here means modded fluids only need a plausible
     * temperature to be handled.
     */
    public static final int HOT_FLUID_TEMPERATURE_K = 700;

    private AcousticMaterials() {}

    /**
     * @return true if this state should block and reflect sound like a solid
     *         block even though the block itself is a fluid.
     */
    public static boolean isAcousticallySolid(IBlockState state) {
        if (state.getMaterial() == Material.LAVA) {
            return true;
        }
        final Block block = state.getBlock();
        if (block instanceof IFluidBlock) {
            final Fluid fluid = ((IFluidBlock) block).getFluid();
            return fluid != null && fluid.getTemperature() >= HOT_FLUID_TEMPERATURE_K;
        }
        return false;
    }
}
