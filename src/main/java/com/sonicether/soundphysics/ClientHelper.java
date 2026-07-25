package com.sonicether.soundphysics;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;

import static com.sonicether.soundphysics.SoundPhysics.getPlayerEyeHeight;

public class ClientHelper {
    public static boolean isInsideOfMaterial(Material materialIn)
    {
        return isInsideOfMaterial(Minecraft.getMinecraft().player, materialIn);
    }

    public static boolean isInsideOfMaterial(EntityPlayer player, Material materialIn)
    {
        if (player == null) {
            return false;
        }
        if (player.getRidingEntity() instanceof EntityBoat)
        {
            return false;
        }
        else
        {
            double d0 = player.posY + (double)(player == Minecraft.getMinecraft().player ? getPlayerEyeHeight() : player.getEyeHeight());
            BlockPos blockpos = new BlockPos(player.posX, d0, player.posZ);
            IBlockState iblockstate = player.world.getBlockState(blockpos);

            Boolean result = iblockstate.getBlock().isEntityInsideMaterial(player.world, blockpos, iblockstate, player, d0, materialIn, true);
            if (result != null) return result;

            if (iblockstate.getMaterial() == materialIn)
            {
                return net.minecraftforge.common.ForgeHooks.isInsideOfMaterial(materialIn, player, blockpos);
            }
            else
            {
                return false;
            }
        }
    }
}
