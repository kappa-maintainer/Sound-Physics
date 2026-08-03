package com.sonicether.soundphysics;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SPMixinConfigPlugin implements IMixinConfigPlugin {

    private static boolean isIC2Classic() {
        if (Loader.isModLoaded("ic2")) {
            Map<String, ModContainer> mods = Loader.instance().getIndexedModList();
            String version = mods.get("ic2").getVersion();
            return !version.endsWith("ex112");
        }
        return false;
    }
    
    @Override
    public void onLoad(String mixinPackage) {
        
    }

    @Override
    @Nullable
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // The snapshot invalidation mixins only make sense while the snapshot
        // scheme is enabled; with the live (unsafe) world mode they are not
        // applied at all.
        if (mixinClassName.endsWith(".MixinChunk")
                || mixinClassName.endsWith(".MixinChunkProviderClient")) {
            return Config.useSnapshot;
        }
        return switch (mixinClassName.split("\\.")[4]) {
            case "glibyfix" -> Loader.isModLoaded("gvc") && Config.glibyVCPatching;
            case "glibysrc" -> Loader.isModLoaded("gvc") && Config.glibyVCSrcPatching;
            case "computronics" -> Loader.isModLoaded("computronics") && Config.computronicsPatching;
            case "umc" -> Loader.isModLoaded("universalmodcore") && Config.irPatching;
            case "midnight" -> Loader.isModLoaded("midnight") && Config.midnightPatching;
            case "enhancedvisuals" -> Loader.isModLoaded("enhancedvisuals") && Config.evPatching;
            case "ic2c" -> Loader.isModLoaded("ic2") && Config.ic2Patching && isIC2Classic();
            case "ic2exp" -> Loader.isModLoaded("ic2") && Config.ic2Patching && !isIC2Classic();
            case "voicechat" -> Loader.isModLoaded("voicechat") && Config.simpleVoiceChatIntegration;
            default -> true;
        };
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }
}
