package com.sonicether.soundphysics;

import java.io.File;

import java.util.*;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.MCVersion;
import zone.rong.mixinbooter.IEarlyMixinLoader;

@MCVersion(value = SoundPhysics.mcVersion)
public class CoreModLoader implements IFMLLoadingPlugin {

	public static File mcDir;

	@Override
	public String[] getASMTransformerClass() {
		//return new String[] { CoreModInjector.class.getName() };
		return null;
	}

	@Override
	public String getModContainerClass() {
		return null;
	}

	@Override
	public String getSetupClass() {
		return null;
	}

	@Override
	public void injectData(final Map<String, Object> data) {
		mcDir = (File)data.get("mcLocation");
	}

	@Override
	public String getAccessTransformerClass() {
		return null;
	}
}
