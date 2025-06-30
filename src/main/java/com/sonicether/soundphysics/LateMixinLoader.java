package com.sonicether.soundphysics;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import zone.rong.mixinbooter.ILateMixinLoader;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class LateMixinLoader implements ILateMixinLoader {
    @Override
    public List<String> getMixinConfigs() {
        return Arrays.asList(
                "soundphysics.gliby.mixin.json",
                "soundphysics.glibysrc.mixin.json",
                "soundphysics.comp.mixin.json",
                "soundphysics.umc.mixin.json",
                "soundphysics.midnight.mixin.json",
                "soundphysics.ic2c.mixin.json",
                "soundphysics.ic2exp.mixin.json",
                "soundphysics.enhancedvisuals.mixin.json"
        );
    }

    static boolean isIC2Classic() {
        if (Loader.isModLoaded("ic2")) {
            Map<String, ModContainer> mods = Loader.instance().getIndexedModList();
            String version = mods.get("ic2").getVersion();
            return !version.endsWith("ex112");
        }
        return false;
    }

    @Override
    public boolean shouldMixinConfigQueue(String mixinConfig) {
        switch (mixinConfig) {
            case "soundphysics.gliby.mixin.json":
                return Loader.isModLoaded("gvc") && Config.glibyVCPatching;
            case "soundphysics.glibysrc.mixin.json":
                return Loader.isModLoaded("gvc") && Config.glibyVCSrcPatching;
            case "soundphysics.comp.mixin.json":
                return Loader.isModLoaded("computronics") && Config.computronicsPatching;
            case "soundphysics.umc.mixin.json":
                return Loader.isModLoaded("universalmodcore") && Config.irPatching;
            case "soundphysics.midnight.mixin.json":
                return Loader.isModLoaded("midnight") && Config.midnightPatching;
            case "soundphysics.enhancedvisuals.mixin.json":
                return Loader.isModLoaded("enhancedvisuals") && Config.evPatching;
            case "soundphysics.ic2c.mixin.json":
                return Loader.isModLoaded("ic2") && Config.ic2Patching && isIC2Classic();
            case "soundphysics.ic2exp.mixin.json":
                return Loader.isModLoaded("ic2") && Config.ic2Patching && !isIC2Classic();
            default: return ILateMixinLoader.super.shouldMixinConfigQueue(mixinConfig);
        }
    }
}
