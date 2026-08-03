package com.sonicether.soundphysics;

import com.sonicether.soundphysics.acoustics.WaterAcoustics;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.config.IConfigElement;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class Config {

	private static final AtomicReference<WaterAcoustics.Settings> waterAcoustics =
			new AtomicReference<>(WaterAcoustics.Settings.defaults());
	public static final Config instance;
	private Configuration forgeConfig;

	// general
	public static float rolloffFactor;
	public static float globalReverbGain;
	public static float globalVolumeMultiplier;
	public static float globalReverbBrightness;
	public static float soundDistanceAllowance;
	public static float globalBlockAbsorption;
	public static float globalBlockReflectance;
	public static float airAbsorption;
	public static float snowAirAbsorptionFactor;
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

	// compatibility
	public static boolean computronicsPatching;
	public static boolean irPatching;
	public static boolean evPatching;
	public static boolean midnightPatching;
	public static boolean midnightPatchingFix;
	public static boolean ic2Patching;
	public static boolean glibyVCPatching;
	public static boolean glibyVCSrcPatching;
	public static boolean hearSelf;
	public static boolean simpleVoiceChatIntegration;
	public static boolean autoSteroDownmix;
	public static String[] downMixBlacklist;

	// reverb
	public static String[] reverbBlacklist;

	// misc
	public static boolean autoSteroDownmixLogging;
	public static boolean injectorLogging;

	// snapshot
	public static boolean useSnapshot;
	public static int snapshotRange;
	public static int snapshotMaxRetainTicks;
	public static float snapshotMaxRetainBlockDistance;

	private static final String categoryGeneral = "General";
	private static final String categoryWaterAcoustics = "Water acoustics";
	private static final String categoryPerformance = "Performance";
	private static final String categoryMaterialProperties = "Material properties";
	private static final String categoryCompatibility = "Compatibility";
	private static final String categoryMisc = "Misc";

	static {
		instance = new Config();
	}

	private Config() {
		this.forgeConfig = new Configuration(new File(new File(CoreModLoader.mcDir, "config"), SoundPhysics.modid+".cfg"));
		syncConfig();
	}

	public void preInit(final FMLPreInitializationEvent event) {
		//this.forgeConfig = new Configuration(event.getSuggestedConfigurationFile());
		syncConfig();
	}

	public void init(final FMLInitializationEvent event) {
		MinecraftForge.EVENT_BUS.register(this);
	}

	@SubscribeEvent
	public void onConfigChanged(final ConfigChangedEvent.OnConfigChangedEvent eventArgs) {
		if (eventArgs.getModID().equals(SoundPhysics.modid)) {
			syncConfig();
		}
	}

	static String getBlacklist() {
		StringBuilder blacklist = new StringBuilder();
		for (String entry : downMixBlacklist) {
			blacklist.append(entry).append("|");
		}
		blacklist.deleteCharAt(blacklist.length() - 1);
		return blacklist.toString();
	}

	private static Pattern reverbBlacklistPattern;

	public static WaterAcoustics.Settings getWaterAcoustics() {
		return waterAcoustics.get();
	}

	static Pattern getReverbBlacklistPattern() {
		if (reverbBlacklist == null || reverbBlacklist.length == 0) {
			return null;
		}
		if (reverbBlacklistPattern == null) {
			StringBuilder sb = new StringBuilder();
			for (String entry : reverbBlacklist) {
				sb.append(entry).append("|");
			}
			sb.deleteCharAt(sb.length() - 1);
			reverbBlacklistPattern = Pattern.compile(sb.toString());
		}
		return reverbBlacklistPattern;
	}

	public List<IConfigElement> getConfigElements() {
		final ArrayList<IConfigElement> list = new ArrayList<IConfigElement>();

		list.add(new ConfigElement(this.forgeConfig.getCategory(Config.categoryGeneral)));
		list.add(new ConfigElement(this.forgeConfig.getCategory(Config.categoryWaterAcoustics)));
		list.add(new ConfigElement(this.forgeConfig.getCategory(Config.categoryPerformance)));
		list.add(new ConfigElement(this.forgeConfig.getCategory(Config.categoryMaterialProperties)));
		list.add(new ConfigElement(this.forgeConfig.getCategory(Config.categoryCompatibility)));
		list.add(new ConfigElement(this.forgeConfig.getCategory(Config.categoryMisc)));

		return list;
	}

	private void syncConfig() {
		// General
		rolloffFactor = this.forgeConfig.getFloat("Attenuation Factor", categoryGeneral, 1.0f, 0.2f, 1.0f,
				"Affects how quiet a sound gets based on distance. Lower values mean distant sounds are louder. 1.0 is the physically correct value.");
		globalVolumeMultiplier = this.forgeConfig.getFloat("Global Volume Multiplier", categoryGeneral, 4.0f, 0.1f, 8.0f,
				"The global volume multiplier of all sounds.");
		globalReverbGain = this.forgeConfig.getFloat("Global Reverb Gain", categoryGeneral, 1.0f, 0.1f, 2.0f,
				"The global volume of simulated reverberations.");
		globalReverbBrightness = this.forgeConfig.getFloat("Global Reverb Brightness", categoryGeneral, 1.0f, 0.1f,
				2.0f,
				"The brightness of reverberation. Higher values result in more high frequencies in reverberation. Lower values give a more muffled sound to the reverb.");
		globalBlockAbsorption = this.forgeConfig.getFloat("Global Block Absorption", categoryGeneral, 1.0f, 0.1f, 4.0f,
				"The global amount of sound that will be absorbed when traveling through blocks.");
		globalBlockReflectance = this.forgeConfig.getFloat("Global Block Reflectance", categoryGeneral, 1.0f, 0.1f,
				4.0f,
				"The global amount of sound reflectance energy of all blocks. Lower values result in more conservative reverb simulation with shorter reverb tails. Higher values result in more generous reverb simulation with higher reverb tails.");
		soundDistanceAllowance = this.forgeConfig.getFloat("Sound Distance Allowance", categoryGeneral, 4.0f, 1.0f,
				6.0f,
				"Minecraft won't allow sounds to play past a certain distance. This parameter is a multiplier for how far away a sound source is allowed to be in order for it to actually play. Values too high can cause polyphony issues.");
		airAbsorption = this.forgeConfig.getFloat("Air Absorption", categoryGeneral, 1.0f, 0.0f, 5.0f,
				"A value controlling the amount that air absorbs high frequencies with distance. A value of 1.0 is physically correct for air with normal humidity and temperature. Higher values mean air will absorb more high frequencies with distance. 0 disables this effect.");
		snowAirAbsorptionFactor = this.forgeConfig.getFloat("Max Snow Air Absorption Factor", categoryGeneral, 5.0f, 0.0f, 10.0f,
				"The maximum air absorption factor when it's snowing. The real absorption factor will depend on the snow's intensity. Set to 1 or lower to disable");
		noteBlockEnable = this.forgeConfig.getBoolean("Affect Note Blocks", categoryGeneral, true,
				"If true, note blocks will be processed.");
		maxDistance = this.forgeConfig.getFloat("Max ray distance", categoryGeneral, 256.0f, 1.0f, 8192.0f,
				"How far the rays should be traced.");
		volumeMulOnlyAffected = this.forgeConfig.getBoolean("Volume Multiplier Only On Affected", categoryGeneral, true,
				"If true, the global volume multiplier will only be applied to affected sounds (so not to the ui sounds for example).");
		globalEchoMultiplier = this.forgeConfig.getFloat("Global Echo Multiplier", categoryGeneral, 1.0f, 0.0f, 2.0f,
				"The global volume multiplier of the echos, put to 0 to disable echos all together");

		boolean pathAbsorptionEnabled = this.forgeConfig.getBoolean("Enable Water Path Absorption", categoryWaterAcoustics, true,
				"Apply distance-dependent high-frequency loss to the part of a direct sound path that travels through water.");
		float waterHfLossPerBlock = this.forgeConfig.getFloat("Water HF Loss Per Block (dB)", categoryWaterAcoustics, 0.03f, 0.0f, 1.0f,
				"Game-scaled high-frequency loss per block of water after the start distance. Real water absorption is much weaker at Minecraft distances.");
		float waterAbsorptionStartDistance = this.forgeConfig.getFloat("Water Absorption Start Distance", categoryWaterAcoustics, 10.0f, 0.0f, 128.0f,
				"Water path length in blocks before distance-dependent high-frequency loss starts.");
		float waterHfLossCap = this.forgeConfig.getFloat("Water HF Loss Cap (dB)", categoryWaterAcoustics, 9.0f, 0.0f, 60.0f,
				"Maximum high-frequency loss caused by distance traveled through water.");
		boolean interfaceEffectsEnabled = this.forgeConfig.getBoolean("Enable Water Interface Effects", categoryWaterAcoustics, true,
				"Apply transmission loss and optional reflected reverb when sound crosses an air-water boundary.");
		float interfaceTransmissionLoss = this.forgeConfig.getFloat("Interface Transmission Loss (dB)", categoryWaterAcoustics, 12.0f, 0.0f, 60.0f,
				"Broadband amplitude loss per counted air-water boundary. About 30 dB approximates ideal normal-incidence physics; the default preserves gameplay audibility.");
		float interfaceHfLoss = this.forgeConfig.getFloat("Interface HF Loss (dB)", categoryWaterAcoustics, 3.0f, 0.0f, 30.0f,
				"Additional game-scaled high-frequency loss per counted air-water boundary.");
		int maxCountedInterfaces = this.forgeConfig.getInt("Max Counted Interfaces", categoryWaterAcoustics, 2, 0, 16,
				"Maximum air-water boundaries that can attenuate one direct sound path.");
		float interfaceReflectionMix = this.forgeConfig.getFloat("Interface Reflection Mix", categoryWaterAcoustics, 0.15f, 0.0f, 1.0f,
				"Amount of rejected interface energy approximated through early reverb sends. Set to 0 to disable this approximation.");
		float interfaceReflectionSendCap = this.forgeConfig.getFloat("Interface Reflection Send Cap", categoryWaterAcoustics, 0.25f, 0.0f, 1.0f,
				"Maximum additional early-reverb send gain produced by water interfaces.");
		boolean listenerColorationEnabled = this.forgeConfig.getBoolean("Enable Underwater Hearing Coloration", categoryWaterAcoustics, true,
				"Color all affected sounds when the listener's ears are underwater.");
		float listenerGainLoss = this.forgeConfig.getFloat("Underwater Hearing Gain Loss (dB)", categoryWaterAcoustics, 2.0f, 0.0f, 30.0f,
				"Broadband hearing loss while the listener is fully underwater.");
		float listenerHfLoss = this.forgeConfig.getFloat("Underwater Hearing HF Loss (dB)", categoryWaterAcoustics, 12.0f, 0.0f, 60.0f,
				"Relative high-frequency hearing loss while the listener is fully underwater.");
		float listenerLfLoss = this.forgeConfig.getFloat("Underwater Hearing LF Loss (dB)", categoryWaterAcoustics, 3.0f, 0.0f, 30.0f,
				"Relative low-frequency hearing loss while the listener is fully underwater, leaving the middle frequencies more prominent.");
		int listenerAttackMs = this.forgeConfig.getInt("Underwater Hearing Attack (ms)", categoryWaterAcoustics, 120, 0, 5000,
				"Time used to blend into underwater hearing coloration.");
		int listenerReleaseMs = this.forgeConfig.getInt("Underwater Hearing Release (ms)", categoryWaterAcoustics, 300, 0, 5000,
				"Time used to blend out of underwater hearing coloration.");
		waterAcoustics.set(new WaterAcoustics.Settings(pathAbsorptionEnabled, waterHfLossPerBlock,
				waterAbsorptionStartDistance, waterHfLossCap, interfaceEffectsEnabled, interfaceTransmissionLoss,
				interfaceHfLoss, maxCountedInterfaces, interfaceReflectionMix, interfaceReflectionSendCap,
				listenerColorationEnabled, listenerGainLoss, listenerHfLoss, listenerLfLoss,
				listenerAttackMs, listenerReleaseMs));

		// performance
		skipRainOcclusionTracing = this.forgeConfig.getBoolean("Skip Rain Occlusion Tracing", categoryPerformance, true,
				"If true, rain sound sources won't trace for sound occlusion. This can help performance during rain.");
		environmentEvaluationRays = this.forgeConfig.getInt("Environment Evaluation Rays", categoryPerformance, 32, 8,
				64,
				"The number of rays to trace to determine reverberation for each sound source. More rays provides more consistent tracing results but takes more time to calculate. Decrease this value if you experience lag spikes when sounds play.");
		simplerSharedAirspaceSimulation = this.forgeConfig.getBoolean("Simpler Shared Airspace Simulation",
				categoryPerformance, false,
				"If true, enables a simpler technique for determining when the player and a sound source share airspace. Might sometimes miss recognizing shared airspace, but it's faster to calculate.");

		// material properties
		stoneReflectivity = this.forgeConfig.getFloat("Stone Reflectivity", categoryMaterialProperties, 0.95f, 0.0f,
				1.0f, "Sound reflectivity for stone blocks.");
		woodReflectivity = this.forgeConfig.getFloat("Wood Reflectivity", categoryMaterialProperties, 0.7f, 0.0f, 1.0f,
				"Sound reflectivity for wooden blocks.");
		groundReflectivity = this.forgeConfig.getFloat("Ground Reflectivity", categoryMaterialProperties, 0.3f, 0.0f,
				1.0f, "Sound reflectivity for ground blocks (dirt, gravel, etc).");
		plantReflectivity = this.forgeConfig.getFloat("Foliage Reflectivity", categoryMaterialProperties, 0.2f, 0.0f,
				1.0f, "Sound reflectivity for foliage blocks (leaves, grass, etc.).");
		metalReflectivity = this.forgeConfig.getFloat("Metal Reflectivity", categoryMaterialProperties, 0.97f, 0.0f,
				1.0f, "Sound reflectivity for metal blocks.");
		glassReflectivity = this.forgeConfig.getFloat("Glass Reflectivity", categoryMaterialProperties, 0.5f, 0.0f,
				1.0f, "Sound reflectivity for glass blocks.");
		clothReflectivity = this.forgeConfig.getFloat("Cloth Reflectivity", categoryMaterialProperties, 0.25f, 0.0f,
				1.0f, "Sound reflectivity for cloth blocks (carpet, wool, etc).");
		sandReflectivity = this.forgeConfig.getFloat("Sand Reflectivity", categoryMaterialProperties, 0.2f, 0.0f, 1.0f,
				"Sound reflectivity for sand blocks.");
		snowReflectivity = this.forgeConfig.getFloat("Snow Reflectivity", categoryMaterialProperties, 0.2f, 0.0f, 1.0f,
				"Sound reflectivity for snow blocks.");

		// compatibility
		computronicsPatching = this.forgeConfig.getBoolean("Patch Computronics", categoryCompatibility, true,
				"REQUIRES RESTART. If true, patches the Computronics sound sources so it works with Sound Physics.");
		irPatching = this.forgeConfig.getBoolean("Patch Immersive Railroading", categoryCompatibility, true,
				"REQUIRES RESTART. If true, patches the Immersive Railroading sound sources so it works with Sound Physics.");
		evPatching = this.forgeConfig.getBoolean("Patch EnhancedVisuals", categoryCompatibility, true,
				"REQUIRES RESTART. If true, patches the EnhancedVisuals sound sources so it works with Sound Physics.");
		midnightPatching = this.forgeConfig.getBoolean("Patch The Midnight", categoryCompatibility, true,
				"REQUIRES RESTART. If true, patches The Midnight to disable redundant functionality that causes some problems.");
		midnightPatchingFix = this.forgeConfig.getBoolean("Readd The Midnight Reverb", categoryCompatibility, true,
				"If true, readds The Midnight reverb that is removed with the patch.");
		ic2Patching = this.forgeConfig.getBoolean("Patch IC2", categoryCompatibility, true,
				"REQUIRES RESTART. If true, patches IC2 audio to better work with Sound Physics.");
		glibyVCPatching = this.forgeConfig.getBoolean("Patch Gliby's Voice Chat", categoryCompatibility, true,
				"REQUIRES RESTART. If true, patches Gliby's VC's copied soundsystem classes to restore Sound Physics.");
		glibyVCSrcPatching = this.forgeConfig.getBoolean("Patch Gliby's VC sources", categoryCompatibility, true,
				"REQUIRES RESTART. If true, patches Gliby's VC sources to work with Sound Physics.");
		simpleVoiceChatIntegration = this.forgeConfig.getBoolean("Simple Voice Chat Integration", categoryCompatibility, true,
				"REQUIRES RESTART. If true, make Sound Physics work with Simple Voice Chat.");
		hearSelf = this.forgeConfig.getBoolean("Can player hear self in Simple Voice Chat", categoryCompatibility, true,
				"REQUIRES RESTART.");
		autoSteroDownmix = this.forgeConfig.getBoolean("Auto Stereo Downmix", categoryCompatibility, true,
				"REQUIRES RESTART. If true, Automatically downmix stereo sounds that are loaded to mono");
		downMixBlacklist = this.forgeConfig.getStringList("Downmix Blacklist", categoryCompatibility, new String[]{
						".*rain.*",
						".*step.*",
						".*block.*",
						".*/ui/.*",
						".*block.note.*",
						"thebetweenlands:sounds/rift_.*\\.ogg",
						".*portal/travel*.*",
						"^soundphysics:.*",
						"^extrasounds:.*",
						"^better_quest_popup:.*",
						"^enhancedvisuals:.*"
				},
				"REQUIRES RESTART. Regex pattern of downmix blacklist, they will be chained with |");

		// reverb
		reverbBlacklist = this.forgeConfig.getStringList("Reverb Blacklist", categoryCompatibility, new String[]{
						"mwc:.*",
						".*rift_idle.*",
						".*rift_unstable.*",
						"better_quest_popup:.*"
				},
				"Regex patterns for sounds that should NOT have reverb, volume multiplier, or attenuation model changes applied (matched against the sound event name).");
		reverbBlacklistPattern = null; // Invalidate cached pattern

		// misc
		autoSteroDownmixLogging = this.forgeConfig.getBoolean("Stereo downmix Logging", categoryMisc, false,
				"If true, Prints sound name and format of the sounds that get converted");
		injectorLogging = this.forgeConfig.getBoolean("Injector Logging", categoryMisc, false,
				"If true, Logs debug info about the injector");

		// snapshot
		useSnapshot = this.forgeConfig.getBoolean("Use World Snapshot", categoryPerformance, true,
				"If true, environment evaluation reads block data from a periodically refreshed immutable snapshot, safe for audio threads. If false, reads the live world directly (unsafe off-main-thread).");
		snapshotRange = this.forgeConfig.getInt("Snapshot Range (chunks)", categoryPerformance, 4, 2, 8,
				"Radius of chunks around the player to snapshot. Larger values cover more area but cost more to rebuild.");
		snapshotMaxRetainTicks = this.forgeConfig.getInt("Snapshot Max Retain Ticks", categoryPerformance, 20, 5, 200,
				"Rebuild the snapshot after this many ticks (20 = 1 second). Lower values are more accurate but cost more CPU.");
		snapshotMaxRetainBlockDistance = this.forgeConfig.getFloat("Snapshot Max Retain Distance", categoryPerformance, 16.0f, 4.0f, 64.0f,
				"Force a snapshot rebuild if the player moved more than this many blocks since the last snapshot.");

		SoundPhysics.applyConfigChanges();
		if (this.forgeConfig.hasChanged()) {
			this.forgeConfig.save();
		}
	}

}
