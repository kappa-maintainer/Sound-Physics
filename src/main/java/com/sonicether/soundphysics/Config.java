package com.sonicether.soundphysics;

import org.apache.commons.lang3.tuple.Pair;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
	public static final ConfigSpec CONFIG;
	public static final ForgeConfigSpec CONFIG_SPEC;

	// general
	public static float referenceDistance;
	public static float rolloffFactor;
	public static float globalReverbGain;
	public static float globalVolumeMultiplier;
	public static float globalReverbBrightness;
	public static float soundDistanceAllowance;
	public static float globalBlockAbsorption;
	public static float globalBlockReflectance;
	public static float airAbsorption;
	public static float snowAirAbsorptionFactor;
	public static float underwaterFilter;
	public static boolean noteBlockEnable;
	public static float maxDistance;
	public static boolean volumeMulOnlyAffected;
	public static float globalEchoMultiplier;

	// performance
	public static boolean skipRainOcclusionTracing;
	public static int environmentEvaluationRays;
	public static boolean simplerSharedAirspaceSimulation;

	// block properties
	public static float stoneReflectivity;
	public static float woodReflectivity;
	public static float groundReflectivity;
	public static float plantReflectivity;
	public static float metalReflectivity;
	public static float glassReflectivity;
	public static float clothReflectivity;
	public static float sandReflectivity;
	public static float snowReflectivity;
	public static float slimeReflectivity;

	// compatibility
	/*public static boolean computronicsPatching;
	public static boolean irPatching;
	public static boolean dsPatching;
	public static boolean midnightPatching;
	public static boolean midnightPatchingFix;
	public static boolean ic2Patching;*/
	public static boolean autoSteroDownmix;
	
	// misc
	public static boolean autoSteroDownmixLogging;

	private static final String categoryGeneral = "General";
	private static final String categoryPerformance = "Performance";
	private static final String categoryMaterialProperties = "Material properties";
	private static final String categoryCompatibility = "Compatibility";
	private static final String categoryMisc = "Misc";

	static {
		final Pair<ConfigSpec, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(ConfigSpec::new);
		CONFIG_SPEC = specPair.getRight();
		CONFIG = specPair.getLeft();
	}

	@SubscribeEvent
	public static void onModConfigEvent(final ModConfig.ModConfigEvent configEvent) {
		if (configEvent.getConfig().getSpec() == Config.CONFIG_SPEC) {
			syncConfig();
		}
	}

	public static void syncConfig() {
		referenceDistance = CONFIG.rolloffFactor.get().floatValue();
		rolloffFactor = CONFIG.rolloffFactor.get().floatValue();
		globalReverbGain = CONFIG.globalReverbGain.get().floatValue();
		globalVolumeMultiplier = CONFIG.globalVolumeMultiplier.get().floatValue();
		globalReverbBrightness = CONFIG.globalReverbBrightness.get().floatValue();
		soundDistanceAllowance = CONFIG.soundDistanceAllowance.get().floatValue();
		globalBlockAbsorption = CONFIG.globalBlockAbsorption.get().floatValue();
		globalBlockReflectance = CONFIG.globalBlockReflectance.get().floatValue();
		airAbsorption = CONFIG.airAbsorption.get().floatValue();
		snowAirAbsorptionFactor = CONFIG.snowAirAbsorptionFactor.get().floatValue();
		underwaterFilter = CONFIG.underwaterFilter.get().floatValue();
		noteBlockEnable = CONFIG.noteBlockEnable.get();
		maxDistance = CONFIG.maxDistance.get().floatValue();
		volumeMulOnlyAffected = CONFIG.volumeMulOnlyAffected.get();
		skipRainOcclusionTracing = CONFIG.skipRainOcclusionTracing.get();
		environmentEvaluationRays = CONFIG.environmentEvaluationRays.get();
		simplerSharedAirspaceSimulation = CONFIG.simplerSharedAirspaceSimulation.get();
		stoneReflectivity = CONFIG.stoneReflectivity.get().floatValue();
		woodReflectivity = CONFIG.woodReflectivity.get().floatValue();
		groundReflectivity = CONFIG.groundReflectivity.get().floatValue();
		plantReflectivity = CONFIG.plantReflectivity.get().floatValue();
		metalReflectivity = CONFIG.metalReflectivity.get().floatValue();
		glassReflectivity = CONFIG.glassReflectivity.get().floatValue();
		clothReflectivity = CONFIG.clothReflectivity.get().floatValue();
		sandReflectivity = CONFIG.sandReflectivity.get().floatValue();
		snowReflectivity = CONFIG.snowReflectivity.get().floatValue();
		slimeReflectivity = CONFIG.slimeReflectivity.get().floatValue();
		/*computronicsPatching = CONFIG.computronicsPatching.get();
		irPatching = CONFIG.irPatching.get();
		dsPatching = CONFIG.dsPatching.get();
		midnightPatching = CONFIG.midnightPatching.get();
		midnightPatchingFix = CONFIG.midnightPatchingFix.get();
		ic2Patching = CONFIG.ic2Patching.get();*/
		autoSteroDownmix = CONFIG.autoSteroDownmix.get();
		autoSteroDownmixLogging = CONFIG.autoSteroDownmixLogging.get();
		SoundPhysics.applyConfigChanges();
	}

	public static class ConfigSpec {

		public static DoubleValue referenceDistance;
		public static DoubleValue rolloffFactor;
		public static DoubleValue globalReverbGain;
		public static DoubleValue globalVolumeMultiplier;
		public static DoubleValue globalReverbBrightness;
		public static DoubleValue soundDistanceAllowance;
		public static DoubleValue globalBlockAbsorption;
		public static DoubleValue globalBlockReflectance;
		public static DoubleValue airAbsorption;
		public static DoubleValue snowAirAbsorptionFactor;
		public static DoubleValue underwaterFilter;
		public static BooleanValue noteBlockEnable;
		public static DoubleValue maxDistance;
		public static BooleanValue volumeMulOnlyAffected;
	
		public static BooleanValue skipRainOcclusionTracing;
		public static IntValue environmentEvaluationRays;
		public static BooleanValue simplerSharedAirspaceSimulation;

		public static DoubleValue stoneReflectivity;
		public static DoubleValue woodReflectivity;
		public static DoubleValue groundReflectivity;
		public static DoubleValue plantReflectivity;
		public static DoubleValue metalReflectivity;
		public static DoubleValue glassReflectivity;
		public static DoubleValue clothReflectivity;
		public static DoubleValue sandReflectivity;
		public static DoubleValue snowReflectivity;
		public static DoubleValue slimeReflectivity;

		/*public static BooleanValue computronicsPatching;
		public static BooleanValue irPatching;
		public static BooleanValue dsPatching;
		public static BooleanValue midnightPatching;
		public static BooleanValue midnightPatchingFix;
		public static BooleanValue ic2Patching;*/
		public static BooleanValue autoSteroDownmix;

		public static BooleanValue autoSteroDownmixLogging;

		public ConfigSpec(ForgeConfigSpec.Builder builder) {
			builder.push(categoryGeneral);
			referenceDistance = builder
					.comment("The distance under which the volume for the sound would normally drop by half (before being influenced by Attenuation Factor). 1.0 is the physically correct value.")
					.defineInRange("Reference Distance", 1.0, 0.2, 2.0);
			rolloffFactor = builder
					.comment("Affects how quiet a sound gets based on distance. Lower values mean distant sounds are louder. 1.0 is the physically correct value.")
					.defineInRange("Attenuation Factor", 1.0, 0.2, 1.0);
			globalVolumeMultiplier = builder
					.comment("The global volume multiplier of all sounds.")
					.defineInRange("Global Volume Multiplier", 4.0, 0.1, 8.0);
			globalReverbGain = builder
					.comment("The global volume of simulated reverberations.")
					.defineInRange("Global Reverb Gain", 1.0, 0.1, 2.0);
			globalReverbBrightness = builder
					.comment("The brightness of reverberation. Higher values result in more high frequencies in reverberation. Lower values give a more muffled sound to the reverb.")
					.defineInRange("Global Reverb Brightness", 1.0, 0.1, 2.0);
			globalBlockAbsorption = builder
					.comment("The global amount of sound that will be absorbed when traveling through blocks.")
					.defineInRange("Global Block Absorption", 1.0, 0.1, 4.0);
			globalBlockReflectance = builder
					.comment("The global amount of sound reflectance energy of all blocks. Lower values result in more conservative reverb simulation with shorter reverb tails. Higher values result in more generous reverb simulation with higher reverb tails.")
					.defineInRange("Global Block Reflectance", 1.0, 0.1, 4.0);
			soundDistanceAllowance = builder
					.comment("Minecraft won't allow sounds to play past a certain distance. This parameter is a multiplier for how far away a sound source is allowed to be in order for it to actually play. Values too high can cause polyphony issues.")
					.defineInRange("Sound Distance Allowance", 4.0, 1.0, 6.0);
			airAbsorption = builder
					.comment("A value controlling the amount that air absorbs high frequencies with distance. A value of 1.0 is physically correct for air with normal humidity and temperature. Higher values mean air will absorb more high frequencies with distance. 0 disables this effect.")
					.defineInRange("Air Absorption", 1.0, 0.0, 5.0);
			snowAirAbsorptionFactor = builder
					.comment("The maximum air absorption factor when it's snowing. The real absorption factor will depend on the snow's intensity. Set to 1 or lower to disable")
					.defineInRange("Max Snow Air Absorption Factor", 5.0, 0.0, 10.0);
			underwaterFilter = builder
					.comment("How much sound is filtered when the player is underwater. 0.0 means no filter. 1.0 means fully filtered.")
					.defineInRange("Underwater Filter", 0.8, 0.0, 1.0);
			noteBlockEnable = builder
					.comment("If true, note blocks will be processed.")
					.define("Affect Note Blocks", true);
			maxDistance = builder
					.comment("How far the rays should be traced.")
					.defineInRange("Max Ray Distance", 256.0, 1.0, 8192.0);
			volumeMulOnlyAffected = builder
					.comment("If true, the global volume multiplier will only be applied to affected sounds (so not to the ui sounds for example).")
					.define("Volume Multiplier Only On Affected", true);
			builder.pop();

			builder.push(categoryPerformance);
			skipRainOcclusionTracing = builder
					.comment("If true, rain sound sources won't trace for sound occlusion. This can help performance during rain.")
					.define("Skip Rain Occlusion Tracing", true);
			environmentEvaluationRays = builder
					.comment("The number of rays to trace to determine reverberation for each sound source. More rays provides more consistent tracing results but takes more time to calculate. Decrease this value if you experience lag spikes when sounds play.")
					.defineInRange("Environment Evaluation Rays", 32, 8, 64);
			simplerSharedAirspaceSimulation = builder
					.comment("If true, enables a simpler technique for determining when the player and a sound source share airspace. Might sometimes miss recognizing shared airspace, but it's faster to calculate.")
					.define("Simpler Shared Airspace Simulation", false);
			builder.pop();

			builder.push(categoryMaterialProperties);
			stoneReflectivity = builder
					.comment("Sound reflectivity for stone blocks.")
					.defineInRange("Stone Reflectivity", 0.95, 0.0, 1.0);
			woodReflectivity = builder
					.comment("Sound reflectivity for wooden blocks.")
					.defineInRange("Wood Reflectivity", 0.7, 0.0, 1.0);
			groundReflectivity = builder
					.comment("Sound reflectivity for ground blocks (dirt, gravel, etc).")
					.defineInRange("Ground Reflectivity", 0.3, 0.0, 1.0);
			plantReflectivity = builder
					.comment("Sound reflectivity for foliage blocks (leaves, grass, etc.).")
					.defineInRange("Foliage Reflectivity", 0.2, 0.0, 1.0);
			metalReflectivity = builder
					.comment("Sound reflectivity for metal blocks.")
					.defineInRange("Metal Reflectivity", 0.97, 0.0, 1.0);
			glassReflectivity = builder
					.comment("Sound reflectivity for glass blocks.")
					.defineInRange("Glass Reflectivity", 0.5, 0.0, 1.0);
			clothReflectivity = builder
					.comment("Sound reflectivity for cloth blocks (carpet, wool, etc).")
					.defineInRange("Cloth Reflectivity", 0.25, 0.0, 1.0);
			sandReflectivity = builder
					.comment("Sound reflectivity for sand blocks.")
					.defineInRange("Sand Reflectivity", 0.2, 0.0, 1.0);
			snowReflectivity = builder
					.comment("Sound reflectivity for snow blocks.")
					.defineInRange("Snow Reflectivity", 0.2, 0.0, 1.0);
			slimeReflectivity = builder
					.comment("Sound reflectivity for slimey blocks.")
					.defineInRange("Slime Reflectivity", 0.3, 0.0, 1.0);
			builder.pop();

			builder.push(categoryCompatibility);
			/*computronicsPatching = builder
					.comment("REQUIRES RESTART. If true, patches the Computronics sound sources so it works with Sound Physics.")
					.define("Patch Computronics", true);
			irPatching = builder
					.comment("REQUIRES RESTART. If true, patches the Immersive Railroading sound sources so it works with Sound Physics.")
					.define("Patch Immersive Railroading", true);
			dsPatching = builder
					.comment("REQUIRES RESTART. If true, patches Dynamic Surroundings to fix some bugs with Sound Physics.")
					.define("Patch Dynamic Surroundings", true);
			midnightPatching = builder
					.comment("REQUIRES RESTART. If true, patches The Midnight to disable redundant functionality that causes some problems.")
					.define("Patch The Midnight", true);
			midnightPatchingFix = builder
					.comment("If true, readds The Midnight reverb that is removed with the patch.")
					.define("Readd The Midnight Reverb", true);
			ic2Patching = builder
					.comment("REQUIRES RESTART. If true, patches IC2 audio to better work with Sound Physics.")
					.define("Patch IC2", true);*/
			autoSteroDownmix = builder
					.comment("REQUIRES RESTART. If true, Automatically downmix stereo sounds that are loaded to mono")
					.define("Auto Stereo Downmix", true);
			builder.pop();

			builder.push(categoryMisc);
			autoSteroDownmixLogging = builder
					.comment("If true, Prints sound name and format of the sounds that get converted")
					.define("Stereo downmix Logging", false);
			builder.pop();

		}

	}

}
