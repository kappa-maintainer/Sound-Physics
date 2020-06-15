package com.sonicether.soundphysics;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

import java.lang.reflect.Field;

import java.util.Set;
import java.util.List;
import java.util.ListIterator;
import java.util.Collections;
import java.util.Optional;

import net.minecraftforge.fml.loading.ModDirTransformerDiscoverer;

import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformer;

public class CoreModTransformationService implements ITransformationService {

	public static Path mcPath;

	@Override
	public String name() {
		return SoundPhysics.modid+"_transformer";
	}

	// Please don't do this, this is a very big hack to get Sound Physics to load as mod alongside the transformation service
	// I repeat, DO NOT DO THIS, this is bad, please don't, i swear on my yeezys
	// But i blame forge for not giving me prorper java transformers like in <=1.12.2
	// And i'm not gonna write a transformer in js bro
	@Override
	public void initialize(IEnvironment environment) {
		try {
			Field field = ModDirTransformerDiscoverer.class.getDeclaredField("transformers");
			field.setAccessible(true);
			List<Path> transformers = (List<Path>)field.get(null);
			Path jarFile = Paths.get(SoundPhysics.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			// Try to remove first just the normal path
			boolean removed = transformers.remove(jarFile);
			if (removed) return;
			// If that doesn't work, try the real path
			removed = transformers.remove(jarFile.toRealPath());
			if (removed) return;
			// And if that still doesn't work, go through and find the file and remove it
			ListIterator<Path> iter = transformers.listIterator();
			while (iter.hasNext()) {
				if (Files.isSameFile(iter.next(),jarFile)) {
					iter.remove();
					removed = true;
				}
			}
			if (!removed) {
				CoreModInjector.logError("Couldn't remove Sound Physics from transformers");
			}
		} catch (Throwable e) {
			CoreModInjector.logError("Failed to remove Sound Physics from the transformers");
			CoreModInjector.logError(e.toString());
		}
	}

	@Override
	public void beginScanning(IEnvironment environment) {
	}

	@Override
	public void onLoad(IEnvironment env, Set<String> otherServices) {
		Optional<Path> path = env.getProperty(IEnvironment.Keys.GAMEDIR.get());
		if (path.isPresent()) mcPath = path.get();
	}

	@Override
	public List<ITransformer> transformers() {
		return Collections.singletonList(new CoreModInjector());
	}

}
