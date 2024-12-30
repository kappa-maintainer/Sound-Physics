package com.sonicether.soundphysics;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

//Server side mod to load the config
@Mod(modid = SoundPhysics.modid, serverSideOnly = true, acceptedMinecraftVersions = SoundPhysics.mcVersion, version = Tags.VERSION,
	acceptableRemoteVersions = "*", guiFactory = "com.sonicether.soundphysics.SPGuiFactory")
public class SoundPhysicsServer {
	@Mod.EventHandler
	public void preInit(final FMLPreInitializationEvent event) {
		SoundPhysics.onServer = true;
		Config.instance.preInit(event);
	}

	@Mod.EventHandler
	public void init(final FMLInitializationEvent event) {
		Config.instance.init(event);
	}
}