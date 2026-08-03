package com.sonicether.soundphysics;

import com.sonicether.soundphysics.acoustics.AcousticPathTracer;
import com.sonicether.soundphysics.acoustics.ListenerWaterState;
import com.sonicether.soundphysics.acoustics.WaterAcoustics;
import com.sonicether.soundphysics.utils.RaycastHelper;
import com.sonicether.soundphysics.utils.SnapshotManager;
import com.sonicether.soundphysics.world.WorldProxy;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.*;
import paulscode.sound.*;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;

@Mod(modid = SoundPhysics.modid, clientSideOnly = true, acceptedMinecraftVersions = SoundPhysics.mcVersion,
	 version = Reference.VERSION, guiFactory = "com.sonicether.soundphysics.SPGuiFactory")
public class SoundPhysics {

	public static final String modid = "soundphysics";
	public static final String version = Reference.VERSION;
	public static final String mcVersion = "1.12.2";

	public static final Logger logger = LogManager.getLogger(modid);

	public static boolean onServer = false;

	public static SoundEvent CLICK = new SoundEvent(new ResourceLocation(modid, "gui_clicks"));

	private static final Pattern rainPattern = Pattern.compile(".*rain.*");
	private static final Pattern stepPattern = Pattern.compile(".*step.*");
	private static final Pattern blockPattern = Pattern.compile(".*block.*");
	private static final Pattern noteBlockPattern = Pattern.compile(".*block.note.*");
	private static final Pattern uiSoundPattern = Pattern.compile(".*(?:gui[._:/-]|ui[._:/-]|soundphysics:(?:click|gui_clicks)).*");

	private static final Pattern allPattern = Pattern.compile(Config.getBlacklist());
	private static Pattern reverbBlacklistPattern;

	public static boolean isSoundBlacklisted() {
		return isSoundBlacklisted(lastSoundName);
	}

	private static boolean isSoundBlacklisted(String soundName) {
		return reverbBlacklistPattern != null && soundName != null && reverbBlacklistPattern.matcher(soundName).matches();
	}

	@Mod.EventHandler
	public void preInit(final FMLPreInitializationEvent event) {
        SoundSystemConfig.setNumberNormalChannels(1024);
        SoundSystemConfig.setNumberStreamingChannels(32);
        Config.instance.preInit(event);
	}

	@Mod.EventHandler
	public void init(final FMLInitializationEvent event) {
		Config.instance.init(event);
	}

	private static int auxFXSlot0;
	private static int auxFXSlot1;
	private static int auxFXSlot2;
	private static int auxFXSlot3;
	private static int reverb0;
	private static int reverb1;
	private static int reverb2;
	private static int reverb3;
	private static int directFilter0;
	private static int sendFilter0;
	private static int sendFilter1;
	private static int sendFilter2;
	private static int sendFilter3;
	private static int mutedSendFilter0;
	private static int mutedSendFilter1;
	private static int mutedSendFilter2;
	private static int mutedSendFilter3;
	private static int maxAuxSends;
	// Direct filters are pooled by quantized parameter class, not per source:
	// the audibly distinct filter states in a scene (occlusion depth, shared
	// airspace, water path) are few, while concurrent sources can be hundreds.
	// Voice-chat channels keep dedicated filters because they run continuously
	// and must not flicker when a game sound updates a shared bucket.
	private static final int MAX_DIRECT_FILTER_BUCKETS = 64;
	private static final int MAX_VOICE_DIRECT_FILTERS = 16;
	private static final Map<DirectFilterKey, SourceFilters> sourceFilters = new ConcurrentHashMap<>();
	private static final Map<Integer, SourceFilters> voiceSourceFilters = new ConcurrentHashMap<>();
	private static final Map<Integer, SourceEnvironment> sourceEnvironments = new ConcurrentHashMap<>();
	private static final Map<Integer, EnvironmentParameters> environmentParameters = new ConcurrentHashMap<>();
	private static long lastSourceReclaimNanos;
	private static final AtomicBoolean sourceFilterCapacityLogged = new AtomicBoolean();
	private static final AtomicBoolean snapshotManagerRegistered = new AtomicBoolean();

	private static Minecraft mc;
	private static SoundSystem sndSystem;

	private static SoundCategory lastSoundCategory;
	private static String lastSoundName;
	private static ISound.AttenuationType lastSoundAtt;

	// THESE VARIABLES ARE CONSTANTLY ACCESSED AND USED BY ASM INJECTED CODE! DO
	// NOT REMOVE!
	public static int attenuationModel = SoundSystemConfig.ATTENUATION_ROLLOFF;
	public static float globalRolloffFactor = Config.rolloffFactor;
	public static float globalVolumeMultiplier0 = Config.globalVolumeMultiplier; // 0 is because of DS trying to read the value of the original name
	public static float globalReverbMultiplier = 0.7f * Config.globalReverbGain;
	public static double soundDistanceAllowance = Config.soundDistanceAllowance;

	/**
	 * CALLED BY ASM INJECTED CODE!
	 */
	public static void init(SoundSystem snds) {
		mc = Minecraft.getMinecraft();
		sndSystem = snds;
		try {
			setupEFX();
			if (isEFXReady()) {
				com.sonicether.soundphysics.voicechat.OpenALSpeakerRouter.markReady();
			}
		} catch (Throwable e) {
			logError("Failed to init EFX");
			logError(e.toString());
		}
		registerSnapshotManager();
	}

	/**
	 * CALLED BY SVC INTEGRATION
	 */
	public static void init() {
		mc = Minecraft.getMinecraft();
		try {
			setupEFX();
			if (isEFXReady()) {
				com.sonicether.soundphysics.voicechat.OpenALSpeakerRouter.markReady();
			}
		} catch (Throwable e) {
			logError("Failed to init EFX");
			logError(e.toString());
		}
		registerSnapshotManager();
	}

	private static void registerSnapshotManager() {
		if (snapshotManagerRegistered.compareAndSet(false, true)) {
			MinecraftForge.EVENT_BUS.register(new SnapshotManager());
		}
	}

	public static void applyConfigChanges() {
		globalRolloffFactor = Config.rolloffFactor;
		globalReverbMultiplier = 0.7f * Config.globalReverbGain;
		soundDistanceAllowance = Config.soundDistanceAllowance;
		globalVolumeMultiplier0 = Config.globalVolumeMultiplier;
		reverbBlacklistPattern = Config.getReverbBlacklistPattern();

		if (auxFXSlot0 != 0) {
			// Set the global reverb parameters and apply them to the effect and
			// effectslot
			setReverbParams(ReverbParams.getReverb0(), auxFXSlot0, reverb0);
			setReverbParams(ReverbParams.getReverb1(), auxFXSlot1, reverb1);
			setReverbParams(ReverbParams.getReverb2(), auxFXSlot2, reverb2);
			setReverbParams(ReverbParams.getReverb3(), auxFXSlot3, reverb3);
		}
	}

	private static void setupEFX() {
		// Get current context and device
		final ALCcontext currentContext = ALC10.alcGetCurrentContext();
		final ALCdevice currentDevice = ALC10.alcGetContextsDevice(currentContext);

		if (ALC10.alcIsExtensionPresent(currentDevice, "ALC_EXT_EFX")) {
			log("EFX Extension recognized.");
		} else {
			logError("EFX Extension not found on current device. Aborting.");
			return;
		}
		IntBuffer buffer = BufferUtils.createIntBuffer(Short.MAX_VALUE + 1);
		ALC10.alcGetInteger(currentDevice, EFX10.ALC_MAX_AUXILIARY_SENDS, buffer);
		maxAuxSends = buffer.get();
		log("Max auxiliary sends: " + maxAuxSends);
		
		// Create auxiliary effect slots
		auxFXSlot0 = EFX10.alGenAuxiliaryEffectSlots();
		log("Aux slot " + auxFXSlot0 + " created");
		EFX10.alAuxiliaryEffectSloti(auxFXSlot0, EFX10.AL_EFFECTSLOT_AUXILIARY_SEND_AUTO, AL10.AL_TRUE);

		auxFXSlot1 = EFX10.alGenAuxiliaryEffectSlots();
		log("Aux slot " + auxFXSlot1 + " created");
		EFX10.alAuxiliaryEffectSloti(auxFXSlot1, EFX10.AL_EFFECTSLOT_AUXILIARY_SEND_AUTO, AL10.AL_TRUE);

		auxFXSlot2 = EFX10.alGenAuxiliaryEffectSlots();
		log("Aux slot " + auxFXSlot2 + " created");
		EFX10.alAuxiliaryEffectSloti(auxFXSlot2, EFX10.AL_EFFECTSLOT_AUXILIARY_SEND_AUTO, AL10.AL_TRUE);

		auxFXSlot3 = EFX10.alGenAuxiliaryEffectSlots();
		log("Aux slot " + auxFXSlot3 + " created");
		EFX10.alAuxiliaryEffectSloti(auxFXSlot3, EFX10.AL_EFFECTSLOT_AUXILIARY_SEND_AUTO, AL10.AL_TRUE);
		checkErrorLog("Failed creating auxiliary effect slots!");

		reverb0 = EFX10.alGenEffects();
		EFX10.alEffecti(reverb0, EFX10.AL_EFFECT_TYPE, EFX10.AL_EFFECT_EAXREVERB);
		checkErrorLog("Failed creating reverb effect slot 0!");
		reverb1 = EFX10.alGenEffects();
		EFX10.alEffecti(reverb1, EFX10.AL_EFFECT_TYPE, EFX10.AL_EFFECT_EAXREVERB);
		checkErrorLog("Failed creating reverb effect slot 1!");
		reverb2 = EFX10.alGenEffects();
		EFX10.alEffecti(reverb2, EFX10.AL_EFFECT_TYPE, EFX10.AL_EFFECT_EAXREVERB);
		checkErrorLog("Failed creating reverb effect slot 2!");
		reverb3 = EFX10.alGenEffects();
		EFX10.alEffecti(reverb3, EFX10.AL_EFFECT_TYPE, EFX10.AL_EFFECT_EAXREVERB);
		checkErrorLog("Failed creating reverb effect slot 3!");

		// Direct filters are source-local. Dynamic send filters are global, while
		// separate muted filters are never modified after initialization.
		directFilter0 = createBandPassFilter();
		sendFilter0 = createBandPassFilter();
		sendFilter1 = createBandPassFilter();
		sendFilter2 = createBandPassFilter();
		sendFilter3 = createBandPassFilter();
		mutedSendFilter0 = createBandPassFilter();
		mutedSendFilter1 = createBandPassFilter();
		mutedSendFilter2 = createBandPassFilter();
		mutedSendFilter3 = createBandPassFilter();
		setBandPassFilter(mutedSendFilter0, 0.0f, 1.0f, 1.0f);
		setBandPassFilter(mutedSendFilter1, 0.0f, 1.0f, 1.0f);
		setBandPassFilter(mutedSendFilter2, 0.0f, 1.0f, 1.0f);
		setBandPassFilter(mutedSendFilter3, 0.0f, 1.0f, 1.0f);
		checkErrorLog("Error creating band-pass filters!");

		applyConfigChanges();
	}

	private static boolean isEFXReady() {
		return auxFXSlot0 != 0 && reverb0 != 0 && directFilter0 != 0;
	}

	private static SoundCategory getSoundCategory(final SoundCategory sc, final String name) {
		if (Config.noteBlockEnable && sc == SoundCategory.RECORDS && noteBlockPattern.matcher(name).matches()) {
			return SoundCategory.BLOCKS;
		} else {
			return sc;
		}
	}

	/**
	 * CALLED BY ASM INJECTED CODE!
	 */
	public static void setLastSound(final ISound snd, final SoundCategory sc, final ResourceLocation soundRes, final ResourceLocation eventRes) {
		lastSoundName = eventRes.toString()+"|"+soundRes.getPath(); // Quick and dirty hack to check the event and sound name
		lastSoundCategory = getSoundCategory(sc,lastSoundName);
		lastSoundAtt = snd.getAttenuationType();
		if (snd instanceof MovingSound)               // Hacky fix until i properly do moving sounds (I'm currently thinking about how)
			lastSoundCategory = SoundCategory.RECORDS;// because all (at least vanilla) moving sounds don't init their position when played
	}

	/**
	 * CALLED BY ASM INJECTED CODE (MixinSoundManager.beforeNewSource)!
	 * Captures sound info from ISound early, before newSource is called,
	 * so that MixinSoundSystem can check the reverb blacklist before changing the attenuation model.
	 */
	public static void setLastSoundFromISound(final ISound snd) {
		ResourceLocation loc = snd.getSoundLocation();
		lastSoundName = loc != null ? loc.toString() : null;
		lastSoundCategory = getSoundCategory(snd.getCategory(), lastSoundName);
		lastSoundAtt = snd.getAttenuationType();
		if (snd instanceof MovingSound)
			lastSoundCategory = SoundCategory.RECORDS;
	}

	/**
	 * CALLED BY ASM INJECTED CODE!
	 */
	public static void setLastSound(final SoundCategory sc, final String soundName) {
		lastSoundCategory = sc;
		lastSoundName = soundName;
		lastSoundAtt = ISound.AttenuationType.LINEAR;
	}

	/**
	 * CALLED BY ASM INJECTED CODE!
	 */
	// For IC2
	public static void setLastSound(final int position, final String soundName) {
		lastSoundCategory = position == 0 ? SoundCategory.BLOCKS : SoundCategory.PLAYERS;
		if (soundName != null)
			lastSoundName = "ic2:"+soundName;
		else // Can't get easliy get sound name for normal IC2 Classic sources
			lastSoundName = "ic2";
		lastSoundAtt = ISound.AttenuationType.LINEAR;
	}

	/**
	 * CALLED BY ASM INJECTED CODE!
	 */
	public static int ic2DistanceCheckHook(float i, float dst, float x1, float y1, float z1, float x2, float y2, float z2) {
		if (i >= dst ||
			(MathHelper.floor(x1) == MathHelper.floor(x2) &&
			MathHelper.floor(y1) == MathHelper.floor(y2) &&
			MathHelper.floor(z1) == MathHelper.floor(z2))) return 1;
		if (dst == i) return 0;
		return -1;
	}

	/**
	 * CALLED BY ASM INJECTED CODE!
	 */
	public static int ic2DistanceCheckHook(double i, double dst, Vec3d p1, Vec3d p2) {
		if (i >= dst ||
			(MathHelper.floor(p1.x) == MathHelper.floor(p2.x) &&
			MathHelper.floor(p1.y) == MathHelper.floor(p2.y) &&
			MathHelper.floor(p1.z) == MathHelper.floor(p2.z))) return 1;
		if (dst == i) return 0;
		return -1;
	}

	/**
	 * CALLED BY ASM INJECTED CODE!
	 */
	public static float applyGlobalVolumeMultiplier(final float volume) {
		if (!Config.volumeMulOnlyAffected || !(mc.player == null || mc.world == null || lastSoundCategory == SoundCategory.MASTER ||
			lastSoundAtt == ISound.AttenuationType.NONE || lastSoundCategory == SoundCategory.RECORDS || lastSoundCategory == SoundCategory.MUSIC ||
			isSoundBlacklisted())) {
			return volume*globalVolumeMultiplier0;
		} else {
			return volume;
		}
	}

	/**
	 * CALLED BY ASM INJECTED CODE!
	 */
	// For sounds that get played normally
	public static void onPlaySound(final float posX, final float posY, final float posZ, final int sourceID) {
		onPlaySound(posX, posY, posZ, sourceID, lastSoundCategory, lastSoundName, lastSoundAtt);
	}

	/**
	 * CALLED BY ASM INJECTED CODE!
	 */
	// For Gliby's VC, sound system source id
	// This is kind of hacked together, it's not great
	public static void onPlaySound(final String id) {
		try { // Getting the sound library here is not ideal but i can't really do better because gibly changes it after startup
			Library sndLibrary = (Library)FieldUtils.readField(sndSystem,"soundLibrary",true);
			if (sndLibrary == null)
				return;

			Source src = sndLibrary.getSource(id);
			if (src == null || src.channel == null)
				return;

			IntBuffer srcid = (IntBuffer)FieldUtils.readField(src.channel,"ALSource");
			if (srcid == null)
				return;

			onPlaySound(src.position.x, src.position.y, src.position.z, srcid.get(0), SoundCategory.PLAYERS, id, ISound.AttenuationType.LINEAR);
		} catch (Exception e) {
			logError("Error trying to get source info");
			logError(e.toString());
		}
	}

	/**
	 * CALLED BY ASM INJECTED CODE!
	 */
	// For sounds that get played using OpenAL directly or just not using the minecraft sound system
	public static void onPlaySoundAL(final float posX, final float posY, final float posZ, final int sourceID) {
		onPlaySound(posX, posY, posZ, sourceID, SoundCategory.AMBIENT, "openal", ISound.AttenuationType.LINEAR);
	}

	/**
	 * CALLED BY ASM INJECTED CODE!
	 */
	public static void onPlaySound(final float posX, final float posY, final float posZ, final int sourceID, SoundCategory soundCat, String soundName, ISound.AttenuationType attType) {
		//log(String.valueOf(posX)+" "+String.valueOf(posY)+" "+String.valueOf(posZ)+" - "+String.valueOf(sourceID)+" - "+soundCat.toString()+" - "+attType.toString()+" - "+soundName);
		sourceEnvironments.put(sourceID, new SourceEnvironment(new Vec3d(posX, posY, posZ), soundCat, soundName, attType));
		evaluateEnvironment(sourceID, posX, posY, posZ, soundCat, soundName, attType);
	}

	public static synchronized void refreshListenerWaterEnvironment() {
		long now = System.nanoTime();
		if (now - lastSourceReclaimNanos >= 1_000_000_000L) {
			lastSourceReclaimNanos = now;
			reclaimInactiveSources();
		}
		WaterAcoustics.Settings settings = Config.getWaterAcoustics();
		float wetness = ListenerWaterState.getWetness(settings);
		// Listener water coloration is a global multiplier: rescale every filter
		// bucket once instead of once per active source.
		if (settings.listenerColorationEnabled()) {
			for (SourceFilters filters : sourceFilters.values()) {
				try {
					filters.rescaleTo(wetness, settings);
				} catch (Throwable t) {
					logger.debug("Unable to rescale direct filter bucket", t);
				}
			}
			for (SourceFilters filters : voiceSourceFilters.values()) {
				try {
					filters.rescaleTo(wetness, settings);
				} catch (Throwable t) {
					logger.debug("Unable to rescale voice direct filter", t);
				}
			}
		}
		// Sources which were previously unfiltered still need listener
		// coloration, but do not need another expensive path trace.
		for (Map.Entry<Integer, SourceEnvironment> entry : sourceEnvironments.entrySet()) {
			int sourceID = entry.getKey();
			SourceEnvironment environment = entry.getValue();
			try {
				if (environmentParameters.get(sourceID) != null) {
					// Its bucket filter was rescaled above.
					continue;
				}
				if (environment.position() == null
						|| (environment.category() != SoundCategory.MASTER
						&& (environment.name() == null || !uiSoundPattern.matcher(environment.name()).matches()))) {
					setListenerColorationOrDefault(sourceID);
				} else {
					setDefaultEnvironment(sourceID, false);
				}
			} catch (Throwable t) {
				logger.debug("Unable to refresh water environment for source " + sourceID, t);
			}
		}
	}

	private static float listenerGain(float lossDb, float wetness) {
		return (float) Math.pow(10.0, -Math.max(0.0, lossDb) * Math.max(0.0, Math.min(1.0, wetness)) / 20.0);
	}

	public static void applyVoiceEnvironment(int sourceID, Vec3d sourcePosition, String category) {
		ensureVoiceFilter(sourceID);
		String soundName = "voicechat:" + (category == null ? "voicechat" : category);
		sourceEnvironments.put(sourceID, new SourceEnvironment(sourcePosition, SoundCategory.PLAYERS,
				soundName, ISound.AttenuationType.LINEAR));
		evaluateEnvironment(sourceID, (float) sourcePosition.x, (float) sourcePosition.y, (float) sourcePosition.z,
				SoundCategory.PLAYERS, soundName, ISound.AttenuationType.LINEAR);
	}

	/**
	 * CALLED BY ASM INJECTED CODE!
	 */
	public static SoundBuffer onLoadSound(SoundBuffer buff, String filename) {
		if (buff == null || buff.audioFormat.getChannels() == 1 || !Config.autoSteroDownmix) return buff;
		if (mc == null || mc.player == null || mc.world == null || lastSoundCategory == SoundCategory.RECORDS || lastSoundCategory == SoundCategory.MUSIC ||
				allPattern.matcher(filename).matches()) {
			if (Config.autoSteroDownmixLogging) log("Not converting sound '"+filename+"'("+buff.audioFormat.toString()+")");
			return buff;
		}
		AudioFormat orignalformat = buff.audioFormat;
		int bits = orignalformat.getSampleSizeInBits();
		boolean bigendian = orignalformat.isBigEndian();
		AudioFormat monoformat = new AudioFormat(orignalformat.getEncoding(), orignalformat.getSampleRate(), bits,
				1, orignalformat.getFrameSize(), orignalformat.getFrameRate(), bigendian);
		if (Config.autoSteroDownmixLogging) log("Converting sound '"+filename+"'("+ orignalformat +") to mono ("+ monoformat +")");

		ByteBuffer bb = ByteBuffer.wrap(buff.audioData,0,buff.audioData.length);
		bb.order(bigendian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
		if (bits == 8) {
			for (int i = 0; i < buff.audioData.length; i+=2) {
				bb.put(i/2,(byte)((bb.get(i)+bb.get(i+1))/2));
			}
		} else if (bits == 16) {
			for (int i = 0; i < buff.audioData.length; i+=4) {
				bb.putShort((i/2),(short)((bb.getShort(i)+bb.getShort(i+2))/2));
			}
		}
		buff.audioFormat = monoformat;
		buff.trimData(buff.audioData.length/2);
		return buff;
	}

	/**
	 * CALLED BY ASM INJECTED CODE!
	 */
	public static double calculateEntitySoundOffset(final Entity entity, final SoundEvent sound) {
		if (sound == null) return entity.getEyeHeight();
		if (stepPattern.matcher(sound.soundName.getPath()).matches()) {
			return 0;
		}

		return entity.getEyeHeight();
	}

	/**
	 * CALLED BY ASM INJECTED CODE!
	 */
	public static Vec3d calculateEntitySoundOffsetVec(final Vec3d pos, final Entity entity, final SoundEvent sound) {
		return new Vec3d(pos.x,pos.y+calculateEntitySoundOffset(entity,sound),pos.z);
	}

	/**
	 * CALLED BY ASM INJECTED CODE!
	 */
	public static Vec3d computronicsOffset(Vec3d or, TileEntity te, PropertyDirection pd) {
		if (!te.hasWorld()) return or;
		EnumFacing ef = te.getWorld().getBlockState(te.getPos()).getValue(pd);
		Vec3d efv = getNormalFromFacing(ef).scale(0.51);
		return or.add(efv);
	}

	// Copy of isRainingAt
	private static boolean isSnowingAt(BlockPos position, boolean check_rain) {
		if ((check_rain && !mc.world.isRaining()) || !mc.world.canSeeSky(position) ||
			mc.world.getPrecipitationHeight(position).getY() > position.getY()) {
			return false;
		} else {
			return mc.world.canSnowAt(position, false) || mc.world.getBiome(position).getEnableSnow();
		}
	}

	@SuppressWarnings("deprecation")
	private static float getBlockReflectivity(final BlockPos blockPos, IBlockAccess world) {
		final Block block = world.getBlockState(blockPos).getBlock();
		final SoundType soundType = block.getSoundType();

		float reflectivity = 0.5f;

		if (soundType == SoundType.STONE) {
			reflectivity = Config.stoneReflectivity;
		} else if (soundType == SoundType.WOOD) {
			reflectivity = Config.woodReflectivity;
		} else if (soundType == SoundType.GROUND) {
			reflectivity = Config.groundReflectivity;
		} else if (soundType == SoundType.PLANT) {
			reflectivity = Config.plantReflectivity;
		} else if (soundType == SoundType.METAL) {
			reflectivity = Config.metalReflectivity;
		} else if (soundType == SoundType.GLASS) {
			reflectivity = Config.glassReflectivity;
		} else if (soundType == SoundType.CLOTH) {
			reflectivity = Config.clothReflectivity;
		} else if (soundType == SoundType.SAND) {
			reflectivity = Config.sandReflectivity;
		} else if (soundType == SoundType.SNOW) {
			reflectivity = Config.snowReflectivity;
		} else if (soundType == SoundType.LADDER) {
			reflectivity = Config.woodReflectivity;
		} else if (soundType == SoundType.ANVIL) {
			reflectivity = Config.metalReflectivity;
		}

		reflectivity *= Config.globalBlockReflectance;

		return reflectivity;
	}

	private static Vec3d getNormalFromFacing(final EnumFacing sideHit) {
		return new Vec3d(sideHit.getDirectionVec());
	}

	private static Vec3d reflect(final Vec3d dir, final Vec3d normal) {
		final double dot2 = dir.dotProduct(normal) * 2;

		final double x = dir.x - dot2 * normal.x;
		final double y = dir.y - dot2 * normal.y;
		final double z = dir.z - dot2 * normal.z;

		return new Vec3d(x, y, z);
	}

	private static Vec3d offsetSoundByName(final double soundX, final double soundY, final double soundZ,
			final Vec3d playerPos, final String name, final SoundCategory category, IBlockAccess world) {
		double offsetX = 0.0;
		double offsetY = 0.0;
		double offsetZ = 0.0;
		double offsetTowardsPlayer = 0.0;

		double tempNormX = 0;
		double tempNormY = 0;
		double tempNormZ = 0;

		if (soundY % 1.0 < 0.001 || stepPattern.matcher(name).matches()) {
			offsetY = 0.225;
		}

		if ((category == SoundCategory.BLOCKS || blockPattern.matcher(name).matches() ||
			(name.equals("openal") && !world.isAirBlock(new BlockPos(soundX,soundY,soundZ)))) &&
			(MathHelper.floor(playerPos.x) != MathHelper.floor(soundX) ||
			 MathHelper.floor(playerPos.y) != MathHelper.floor(soundY) ||
			 MathHelper.floor(playerPos.z) != MathHelper.floor(soundZ))) {
			// The ray will probably hit the block that it's emitting from
			// before
			// escaping. Offset the ray start position towards the player by the
			// diagonal half length of a cube

			tempNormX = playerPos.x - soundX;
			tempNormY = playerPos.y - soundY;
			tempNormZ = playerPos.z - soundZ;
			final double length = Math.sqrt(tempNormX * tempNormX + tempNormY * tempNormY + tempNormZ * tempNormZ);
			tempNormX /= length;
			tempNormY /= length;
			tempNormZ /= length;
			// 0.867 > square root of 0.5^2 * 3
			offsetTowardsPlayer = 0.867;
			offsetX += tempNormX * offsetTowardsPlayer;
			offsetY += tempNormY * offsetTowardsPlayer;
			offsetZ += tempNormZ * offsetTowardsPlayer;
		}

		return new Vec3d(soundX + offsetX, soundY + offsetY, soundZ + offsetZ);
	}

	public static float getPlayerEyeHeight() throws IllegalStateException {
		ReentrantReadWriteLock lock = (ReentrantReadWriteLock)mc.player.getDataManager().lock;
		if (lock.isWriteLocked()) {
			return mc.player.getDefaultEyeHeight();
			//logError("Deadlock detected, avoiding it by throwing exception");
			//throw new IllegalStateException("Player's Data Mananger is write locked");
		}
		return mc.player.getEyeHeight();
	}

	@SuppressWarnings("deprecation")
	private static void evaluateEnvironment(final int sourceID, final float posX, final float posY, final float posZ, final SoundCategory category,
											final String name, ISound.AttenuationType attType) {
		try {
		    if (mc.player == null || mc.world == null || name == null || category == SoundCategory.MASTER || attType == ISound.AttenuationType.NONE) {
				setDefaultEnvironment(sourceID, false);
				return;
			}

			if (isSoundBlacklisted(name)) {
				setDefaultEnvironment(sourceID, false);
				return;
			}

			if (category == SoundCategory.MASTER || uiSoundPattern.matcher(name).matches()
					|| category == SoundCategory.RECORDS || category == SoundCategory.MUSIC) {
				if (category == SoundCategory.RECORDS || category == SoundCategory.MUSIC) {
					setListenerColorationOrDefault(sourceID);
				} else {
					setDefaultEnvironment(sourceID, false);
				}
				return;
			}

			final IBlockAccess world = WorldProxy.getOrFallback();
			if (world == null) {
				setDefaultEnvironment(sourceID, false);
				return;
			}

			final boolean isRain = rainPattern.matcher(name).matches();

			float directCutoff = 1.0f;
			final float absorptionCoeff = Config.globalBlockAbsorption * 3.0f;

			Vec3d snapshotPlayerPos = WorldProxy.getPlayerPos();
			final Vec3d playerPos = snapshotPlayerPos != null ? snapshotPlayerPos
					: new Vec3d(mc.player.posX, mc.player.posY + getPlayerEyeHeight(), mc.player.posZ);
			final Vec3d soundPos = offsetSoundByName(posX, posY, posZ, playerPos, name, category, world);
			final Vec3d normalToPlayer = playerPos.subtract(soundPos).normalize();
			final AcousticPathTracer.Result acousticPath = AcousticPathTracer.trace(world, soundPos, playerPos);
			final WaterAcoustics.Settings waterSettings = Config.getWaterAcoustics();
			final float listenerWetness = ListenerWaterState.getWetness(waterSettings);
			final WaterAcoustics.Effect waterEffect = WaterAcoustics.calculate(acousticPath, waterSettings, listenerWetness);

			float airAbsorptionFactor = 1.0f;

			if (Config.snowAirAbsorptionFactor > 1.0f && mc.world.isRaining()) {
				final Vec3d middlePos = playerPos.add(soundPos).scale(0.5);
				final BlockPos playerPosBlock = new BlockPos(playerPos);
				final BlockPos soundPosBlock = new BlockPos(soundPos);
				final BlockPos middlePosBlock = new BlockPos(middlePos);
				final int snowingPlayer = isSnowingAt(playerPosBlock,false) ? 1 : 0;
				final int snowingSound = isSnowingAt(soundPosBlock,false) ? 1 : 0;
				final int snowingMiddle = isSnowingAt(middlePosBlock,false) ? 1 : 0;
				final float snowFactor = snowingPlayer * 0.25f + snowingMiddle * 0.5f + snowingSound * 0.25f;
				airAbsorptionFactor = Math.max(Config.snowAirAbsorptionFactor*mc.world.getRainStrength(1.0f)*snowFactor,airAbsorptionFactor);
			}

			Vec3d rayOrigin = soundPos;

			float occlusionAccumulation = 0.0f;

			if (!Config.skipRainOcclusionTracing || !isRain) {
				for (int i = 0; i < 10; i++) {
					final RayTraceResult rayHit = RaycastHelper.rayTraceBlocks(world, rayOrigin, playerPos, false);

					if (rayHit == null) {
						break;
					}

					final Block blockHit = world.getBlockState(rayHit.getBlockPos()).getBlock();

					if (blockHit.isOpaqueCube(blockHit.getDefaultState())) {
						occlusionAccumulation += 1.0f;
					}

					rayOrigin = new Vec3d(rayHit.hitVec.x + normalToPlayer.x * 0.1, rayHit.hitVec.y + normalToPlayer.y * 0.1,
							rayHit.hitVec.z + normalToPlayer.z * 0.1);
				}
			}

			directCutoff = (float) Math.exp(-occlusionAccumulation * absorptionCoeff);
			float directGain = (float) Math.pow(directCutoff, 0.1);

			// Calculate reverb parameters for this sound
			float sendGain0 = 0.0f;
			float sendGain1 = 0.0f;
			float sendGain2 = 0.0f;
			float sendGain3 = 0.0f;

			float sendCutoff0 = 1.0f;
			float sendCutoff1 = 1.0f;
			float sendCutoff2 = 1.0f;
			float sendCutoff3 = 1.0f;

			if (isRain) {
				directCutoff *= waterEffect.highFrequencyGain();
				directGain *= waterEffect.directGain();
				cacheEnvironmentParameters(sourceID, sendGain0, sendGain1, sendGain2, sendGain3,
						sendCutoff0, sendCutoff1, sendCutoff2, sendCutoff3, directCutoff, directGain,
						waterEffect.lowFrequencyGain(), listenerWetness, airAbsorptionFactor);
				setEnvironment(sourceID, sendGain0, sendGain1, sendGain2, sendGain3, sendCutoff0, sendCutoff1, sendCutoff2,
						sendCutoff3, directCutoff, directGain, waterEffect.lowFrequencyGain(), airAbsorptionFactor, listenerWetness);
				return;
			}

			// Shoot rays around sound
			final float phi = 1.618033988f;
			final float gAngle = phi * (float) Math.PI * 2.0f;
			final float maxDistance = Config.maxDistance;

			final int numRays = Config.environmentEvaluationRays;
			final int rayBounces = 4;

			final float[] bounceReflectivityRatio = new float[rayBounces];

			float sharedAirspace = 0.0f;

			final float rcpTotalRays = 1.0f / (numRays * rayBounces);
			final float rcpPrimaryRays = 1.0f / numRays;

			for (int i = 0; i < numRays; i++) {
                final float fiN = (float) i / numRays;
				final float longitude = gAngle * (float) i;
				final float latitude = (float) Math.asin(fiN * 2.0f - 1.0f);

				final Vec3d rayDir = new Vec3d(Math.cos(latitude) * Math.cos(longitude),
						Math.cos(latitude) * Math.sin(longitude), Math.sin(latitude));

				final Vec3d rayStart = new Vec3d(soundPos.x, soundPos.y, soundPos.z);

				final Vec3d rayEnd = new Vec3d(rayStart.x + rayDir.x * maxDistance, rayStart.y + rayDir.y * maxDistance,
						rayStart.z + rayDir.z * maxDistance);

				final RayTraceResult rayHit = RaycastHelper.rayTraceBlocks(world, rayStart, rayEnd, false);

				if (rayHit != null) {
					final double rayLength = soundPos.distanceTo(rayHit.hitVec);

					// Additional bounces
					BlockPos lastHitBlock = rayHit.getBlockPos();
					Vec3d lastHitPos = rayHit.hitVec;
					Vec3d lastHitNormal = getNormalFromFacing(rayHit.sideHit);
					Vec3d lastRayDir = rayDir;

					float totalRayDistance = (float) rayLength;

					// Secondary ray bounces
					for (int j = 0; j < rayBounces; j++) {
						final Vec3d newRayDir = reflect(lastRayDir, lastHitNormal);
						// Vec3d newRayDir = lastHitNormal;
						final Vec3d newRayStart = new Vec3d(lastHitPos.x + lastHitNormal.x * 0.01,
								lastHitPos.y + lastHitNormal.y * 0.01, lastHitPos.z + lastHitNormal.z * 0.01);
						final Vec3d newRayEnd = new Vec3d(newRayStart.x + newRayDir.x * maxDistance,
								newRayStart.y + newRayDir.y * maxDistance, newRayStart.z + newRayDir.z * maxDistance);

						final RayTraceResult newRayHit = RaycastHelper.rayTraceBlocks(world, newRayStart, newRayEnd, false);

						float energyTowardsPlayer = 0.25f;
						final float blockReflectivity = getBlockReflectivity(lastHitBlock, world);
						energyTowardsPlayer *= blockReflectivity * 0.75f + 0.25f;

						if (newRayHit == null) {
							totalRayDistance += (float) lastHitPos.distanceTo(playerPos);
						} else {
							final double newRayLength = lastHitPos.distanceTo(newRayHit.hitVec);

							bounceReflectivityRatio[j] += blockReflectivity;

							totalRayDistance += (float) newRayLength;

							lastHitPos = newRayHit.hitVec;
							lastHitNormal = getNormalFromFacing(newRayHit.sideHit);
							lastRayDir = newRayDir;
							lastHitBlock = newRayHit.getBlockPos();

							// Cast one final ray towards the player. If it's
							// unobstructed, then the sound source and the player
							// share airspace.
							if (!Config.simplerSharedAirspaceSimulation || j == rayBounces - 1) {
								final Vec3d finalRayStart = new Vec3d(lastHitPos.x + lastHitNormal.x * 0.01,
										lastHitPos.y + lastHitNormal.y * 0.01, lastHitPos.z + lastHitNormal.z * 0.01);

								final RayTraceResult finalRayHit = RaycastHelper.rayTraceBlocks(world, finalRayStart, playerPos, false);

								if (finalRayHit == null) {
									// log("Secondary ray hit the player!");
									sharedAirspace += 1.0f;
								}
							}
						}

						final float reflectionDelay = (float) Math.max(totalRayDistance, 0.0) * 0.12f * blockReflectivity;

						final float cross0 = 1.0f - MathHelper.clamp(Math.abs(reflectionDelay - 0.0f), 0.0f, 1.0f);
						final float cross1 = 1.0f - MathHelper.clamp(Math.abs(reflectionDelay - 1.0f), 0.0f, 1.0f);
						final float cross2 = 1.0f - MathHelper.clamp(Math.abs(reflectionDelay - 2.0f), 0.0f, 1.0f);
						final float cross3 = MathHelper.clamp(reflectionDelay - 2.0f, 0.0f, 1.0f);

						sendGain0 += cross0 * energyTowardsPlayer * 6.4f * rcpTotalRays;
						sendGain1 += cross1 * energyTowardsPlayer * 12.8f * rcpTotalRays;
						sendGain2 += cross2 * energyTowardsPlayer * 12.8f * rcpTotalRays;
						sendGain3 += cross3 * energyTowardsPlayer * 12.8f * rcpTotalRays;

						// Nowhere to bounce off of, stop bouncing!
						if (newRayHit == null) {
							break;
						}
					}
				}

			}

			// log("total reflectivity ratio: " + totalReflectivityRatio);

			bounceReflectivityRatio[0] = bounceReflectivityRatio[0] / numRays;
			bounceReflectivityRatio[1] = bounceReflectivityRatio[1] / numRays;
			bounceReflectivityRatio[2] = bounceReflectivityRatio[2] / numRays;
			bounceReflectivityRatio[3] = bounceReflectivityRatio[3] / numRays;

			sharedAirspace *= 64.0f;

			if (Config.simplerSharedAirspaceSimulation) {
				sharedAirspace *= rcpPrimaryRays;
			} else {
				sharedAirspace *= rcpTotalRays;
			}

			final float sharedAirspaceWeight0 = MathHelper.clamp(sharedAirspace / 20.0f, 0.0f, 1.0f);
			final float sharedAirspaceWeight1 = MathHelper.clamp(sharedAirspace / 15.0f, 0.0f, 1.0f);
			final float sharedAirspaceWeight2 = MathHelper.clamp(sharedAirspace / 10.0f, 0.0f, 1.0f);
			final float sharedAirspaceWeight3 = MathHelper.clamp(sharedAirspace / 10.0f, 0.0f, 1.0f);

			sendCutoff0 = (float) Math.exp(-occlusionAccumulation * absorptionCoeff * 1.0f) * (1.0f - sharedAirspaceWeight0)
					+ sharedAirspaceWeight0;
			sendCutoff1 = (float) Math.exp(-occlusionAccumulation * absorptionCoeff * 1.0f) * (1.0f - sharedAirspaceWeight1)
					+ sharedAirspaceWeight1;
			sendCutoff2 = (float) Math.exp(-occlusionAccumulation * absorptionCoeff * 1.5f) * (1.0f - sharedAirspaceWeight2)
					+ sharedAirspaceWeight2;
			sendCutoff3 = (float) Math.exp(-occlusionAccumulation * absorptionCoeff * 1.5f) * (1.0f - sharedAirspaceWeight3)
					+ sharedAirspaceWeight3;

			// attempt to preserve directionality when airspace is shared by
			// allowing some of the dry signal through but filtered
			final float averageSharedAirspace = (sharedAirspaceWeight0 + sharedAirspaceWeight1 + sharedAirspaceWeight2
					+ sharedAirspaceWeight3) * 0.25f;
			directCutoff = Math.max((float) Math.pow(averageSharedAirspace, 0.5) * 0.2f, directCutoff);

			directGain = (float) Math.pow(directCutoff, 0.1);

			sendGain1 *= bounceReflectivityRatio[1];
			sendGain2 *= (float) Math.pow(bounceReflectivityRatio[2], 3.0);
			sendGain3 *= (float) Math.pow(bounceReflectivityRatio[3], 4.0);

			sendGain0 = MathHelper.clamp(sendGain0, 0.0f, 1.0f);
			sendGain1 = MathHelper.clamp(sendGain1, 0.0f, 1.0f);
			sendGain2 = MathHelper.clamp(sendGain2 * 1.05f - 0.05f, 0.0f, 1.0f);
			sendGain3 = MathHelper.clamp(sendGain3 * 1.05f - 0.05f, 0.0f, 1.0f);

			sendGain0 *= (float) Math.pow(sendCutoff0, 0.1);
			sendGain1 *= (float) Math.pow(sendCutoff1, 0.1);
			sendGain2 *= (float) Math.pow(sendCutoff2, 0.1);
			sendGain3 *= (float) Math.pow(sendCutoff3, 0.1);

			sendGain0 = MathHelper.clamp(sendGain0 * waterEffect.directGain() + waterEffect.reflectionSendGain(), 0.0f, 1.0f);
			sendGain1 = MathHelper.clamp(sendGain1 * waterEffect.directGain() + waterEffect.reflectionSendGain() * 0.5f, 0.0f, 1.0f);
			sendGain2 *= waterEffect.directGain();
			sendGain3 *= waterEffect.directGain();
			sendCutoff0 *= waterEffect.highFrequencyGain();
			sendCutoff1 *= waterEffect.highFrequencyGain();
			sendCutoff2 *= waterEffect.highFrequencyGain();
			sendCutoff3 *= waterEffect.highFrequencyGain();

			if (Config.midnightPatching && Config.midnightPatchingFix && mc.world.provider.getDimensionType().getName().equals("midnight")) {
				// Since the patch removes the incompatble reverb, readd some reverb everywhere
				// It's not a great fix but it works fine
				sendGain1 = MathHelper.clamp(sendGain1, 0.3f, 1.0f);
				sendGain2 = MathHelper.clamp(sendGain2, 0.5f, 1.0f);
				sendGain3 = MathHelper.clamp(sendGain3, 0.7f, 1.0f);
				sendCutoff1 = MathHelper.clamp(sendCutoff1, 0.3f, 1.0f);
				sendCutoff2 = MathHelper.clamp(sendCutoff2, 0.5f, 1.0f);
				sendCutoff3 = MathHelper.clamp(sendCutoff3, 0.7f, 1.0f);
			}

			directGain *= waterEffect.directGain();
			directCutoff *= waterEffect.highFrequencyGain();

			cacheEnvironmentParameters(sourceID, sendGain0, sendGain1, sendGain2, sendGain3,
					sendCutoff0, sendCutoff1, sendCutoff2, sendCutoff3, directCutoff, directGain,
					waterEffect.lowFrequencyGain(), listenerWetness, airAbsorptionFactor);
			setEnvironment(sourceID, sendGain0, sendGain1, sendGain2, sendGain3, sendCutoff0, sendCutoff1, sendCutoff2,
					sendCutoff3, directCutoff, directGain, waterEffect.lowFrequencyGain(), airAbsorptionFactor, listenerWetness);
		} catch(Exception e) {
			logger.error("Error while evaluation environment:", e);
            logger.error(e.getMessage());
			setDefaultEnvironment(sourceID, false);
		}
	}

	private static void cacheEnvironmentParameters(int sourceID,
			float sendGain0, float sendGain1, float sendGain2, float sendGain3,
			float sendCutoff0, float sendCutoff1, float sendCutoff2, float sendCutoff3,
			float directCutoff, float directGain, float lowFrequencyGain,
			float listenerWetness, float airAbsorptionFactor) {
		environmentParameters.put(sourceID, new EnvironmentParameters(sendGain0, sendGain1, sendGain2, sendGain3,
				sendCutoff0, sendCutoff1, sendCutoff2, sendCutoff3, directCutoff, directGain,
				lowFrequencyGain, listenerWetness, airAbsorptionFactor));
	}

	public static void setListenerColorationOrDefault(int sourceID) {
		sourceEnvironments.put(sourceID, new SourceEnvironment(null, SoundCategory.MASTER, "listener", ISound.AttenuationType.NONE));
		WaterAcoustics.Settings settings = Config.getWaterAcoustics();
		if (ListenerWaterState.getWetness(settings) <= 1.0E-4f) {
			setDefaultEnvironment(sourceID, false);
			return;
		}
		float wetness = ListenerWaterState.getWetness(settings);
		if (wetness <= 1.0E-4f) {
			setDefaultEnvironment(sourceID, false);
			return;
		}
		WaterAcoustics.Effect effect = WaterAcoustics.calculate(
				new AcousticPathTracer.Result(0.0, 0.0, 0, false, false, false), settings, wetness);
		attachMutedSendFilters(sourceID);
		setDirectEnvironment(sourceID, effect.highFrequencyGain(), effect.directGain(), effect.lowFrequencyGain(), wetness);
	}

	public static void setDefaultEnvironment(int sourceID) {
		setDefaultEnvironment(sourceID, false);
	}

	public static void setDefaultEnvironment(int sourceID, boolean auxOnly) {
		if (auxOnly) {
			setEnvironment(sourceID, 0F, 0F, 0F, 0F, 1F, 1F, 1F, 1F, 1F, 0F, 1F, 1F, 0F);
			return;
		}
		clearEnvironment(sourceID);
	}

	public static synchronized void registerVoiceSource(int sourceID) {
		// Voice channels get a dedicated direct filter so their long-running
		// audio never shares a game-sound bucket (which would flicker when a
		// bucket is rewritten by another source).
		ensureVoiceFilter(sourceID);
	}

	public static synchronized void unregisterVoiceSource(int sourceID) {
		releaseSource(sourceID);
	}

	public static synchronized void releaseSource(int sourceID) {
		sourceEnvironments.remove(sourceID);
		environmentParameters.remove(sourceID);
		clearEnvironment(sourceID);
	}

	private static synchronized void reclaimInactiveSources() {
		for (Integer sourceID : sourceEnvironments.keySet()) {
			reclaimIfInactive(sourceID);
		}
	}

	private static void reclaimIfInactive(int sourceID) {
		try {
			int state = AL10.alGetSourcei(sourceID, AL10.AL_SOURCE_STATE);
			if (state != AL10.AL_PLAYING && state != AL10.AL_PAUSED) {
				releaseSource(sourceID);
			}
		} catch (Throwable ignored) {
			releaseSource(sourceID);
		}
	}

	private static SourceFilters acquireDirectFilter(int sourceID, float directGain, float directCutoff,
			float lowFrequencyGain, float listenerWetness) {
		SourceFilters voice = voiceSourceFilters.get(sourceID);
		if (voice != null) {
			voice.set(directGain, directCutoff, lowFrequencyGain, listenerWetness);
			voice.markUsed();
			return voice;
		}
		DirectFilterKey key = DirectFilterKey.of(directGain, directCutoff);
		SourceFilters existing = sourceFilters.get(key);
		if (existing != null) {
			// Same parameter class: refresh in place. All attached sources hear
			// the update, which is coherent because they are within quantization
			// of each other.
			existing.set(directGain, directCutoff, lowFrequencyGain, listenerWetness);
			existing.markUsed();
			return existing;
		}
		if (sourceFilters.size() >= MAX_DIRECT_FILTER_BUCKETS) {
			reclaimInactiveSources();
			// Evict the least-recently-used bucket and reuse its filter object.
			SourceFilters victim = evictLeastRecentlyUsedBucket();
			if (victim != null) {
				victim.set(directGain, directCutoff, lowFrequencyGain, listenerWetness);
				victim.markUsed();
				sourceFilters.put(key, victim);
				return victim;
			}
			if (sourceFilterCapacityLogged.compareAndSet(false, true)) {
				logger.warn("OpenAL direct-filter bucket capacity reached; additional sources will fall back to a shared filter");
			}
			return null;
		}
		SourceFilters filters = createSourceFilters();
		if (filters == null) {
			return null;
		}
		filters.set(directGain, directCutoff, lowFrequencyGain, listenerWetness);
		filters.markUsed();
		sourceFilters.put(key, filters);
		checkErrorLog("Error creating direct filter for source: " + sourceID);
		return filters;
	}

	private static SourceFilters evictLeastRecentlyUsedBucket() {
		Map.Entry<DirectFilterKey, SourceFilters> oldest = null;
		long oldestNanos = Long.MAX_VALUE;
		for (Map.Entry<DirectFilterKey, SourceFilters> entry : sourceFilters.entrySet()) {
			long used = entry.getValue().lastUsedNanos;
			if (used < oldestNanos) {
				oldestNanos = used;
				oldest = entry;
			}
		}
		if (oldest == null) {
			return null;
		}
		sourceFilters.remove(oldest.getKey());
		return oldest.getValue();
	}

	private static SourceFilters createSourceFilters() {
		SourceFilters filters = new SourceFilters();
		filters.direct = createBandPassFilter();
		if (!filters.valid()) {
			filters.delete();
			return null;
		}
		return filters;
	}

	private static void ensureVoiceFilter(int sourceID) {
		if (voiceSourceFilters.containsKey(sourceID) || voiceSourceFilters.size() >= MAX_VOICE_DIRECT_FILTERS) {
			return;
		}
		SourceFilters filters = createSourceFilters();
		if (filters != null) {
			voiceSourceFilters.put(sourceID, filters);
		}
	}

	private static int createBandPassFilter() {
		int filter = EFX10.alGenFilters();
		EFX10.alFilteri(filter, EFX10.AL_FILTER_TYPE, EFX10.AL_FILTER_BANDPASS);
		return filter;
	}

	private static synchronized void clearEnvironment(final int sourceID) {
		environmentParameters.remove(sourceID);
		AL10.alSourcei(sourceID, EFX10.AL_DIRECT_FILTER, 0);
		// Filter 0 means "unfiltered", not "no send". Use immutable zero-gain
		// filters or UI sounds would still feed the reverb effect at full volume.
		attachMutedSendFilters(sourceID);
		// Game-sound buckets are shared and stay cached; only the dedicated
		// voice-channel filter is owned by the source and must be freed here.
		SourceFilters voice = voiceSourceFilters.remove(sourceID);
		if (voice != null) {
			voice.delete();
		}
	}

	private static synchronized void setEnvironment(final int sourceID, final float sendGain0, final float sendGain1,
			final float sendGain2, final float sendGain3, final float sendCutoff0, final float sendCutoff1,
			final float sendCutoff2, final float sendCutoff3, final float directCutoff, final float directGain,
			final float lowFrequencyGain, final float airAbsorptionFactor, final float listenerWetness) {
		boolean needsDirectFilter = directGain < 0.9999f || directCutoff < 0.9999f || lowFrequencyGain < 0.9999f;
		boolean needsSendFilter = sendGain0 > 0.0001f || sendGain1 > 0.0001f || sendGain2 > 0.0001f || sendGain3 > 0.0001f;
		SourceFilters filters = needsDirectFilter
				? acquireDirectFilter(sourceID, directGain, directCutoff, lowFrequencyGain, listenerWetness)
				: null;
		if (filters != null) {
			AL10.alSourcei(sourceID, EFX10.AL_DIRECT_FILTER, filters.direct);
		} else if (needsDirectFilter) {
			// Bucket allocation failed: fall back to the shared filter so the
			// source stays muffled instead of playing completely unfiltered.
			setBandPassFilter(directFilter0, directGain, directCutoff, lowFrequencyGain);
			AL10.alSourcei(sourceID, EFX10.AL_DIRECT_FILTER, directFilter0);
		} else {
			AL10.alSourcei(sourceID, EFX10.AL_DIRECT_FILTER, 0);
		}
		if (needsSendFilter) {
			// EFX has a small global filter budget. Keep the four send filters
			// global; direct water coloration remains source-local and exact.
			if (maxAuxSends >= 4) {
				setBandPassFilter(sendFilter0, sendGain0, sendCutoff0, lowFrequencyGain);
				AL11.alSource3i(sourceID, EFX10.AL_AUXILIARY_SEND_FILTER, auxFXSlot0, 3, sendFilter0);
			}
			if (maxAuxSends >= 3) {
				setBandPassFilter(sendFilter1, sendGain1, sendCutoff1, lowFrequencyGain);
				AL11.alSource3i(sourceID, EFX10.AL_AUXILIARY_SEND_FILTER, auxFXSlot1, 2, sendFilter1);
			}
			if (maxAuxSends >= 2) {
				setBandPassFilter(sendFilter2, sendGain2, sendCutoff2, lowFrequencyGain);
				AL11.alSource3i(sourceID, EFX10.AL_AUXILIARY_SEND_FILTER, auxFXSlot2, 1, sendFilter2);
			}
			if (maxAuxSends >= 1) {
				setBandPassFilter(sendFilter3, sendGain3, sendCutoff3, lowFrequencyGain);
				AL11.alSource3i(sourceID, EFX10.AL_AUXILIARY_SEND_FILTER, auxFXSlot3, 0, sendFilter3);
			}
		} else {
			attachMutedSendFilters(sourceID);
		}

		AL10.alSourcef(sourceID, EFX10.AL_AIR_ABSORPTION_FACTOR, MathHelper.clamp(Config.airAbsorption * airAbsorptionFactor,0.0f,10.0f));
		checkErrorLog("Error while setting environment for source: " + sourceID);
	}

	private static synchronized void setDirectEnvironment(int sourceID, float directCutoff,
			float directGain, float lowFrequencyGain, float listenerWetness) {
		boolean needsDirectFilter = directGain < 0.9999f || directCutoff < 0.9999f || lowFrequencyGain < 0.9999f;
		if (!needsDirectFilter) {
			AL10.alSourcei(sourceID, EFX10.AL_DIRECT_FILTER, 0);
			return;
		}
		SourceFilters filters = acquireDirectFilter(sourceID, directGain, directCutoff, lowFrequencyGain, listenerWetness);
		if (filters == null) {
			// Bucket allocation failed: keep the source muffled via the shared
			// filter instead of playing completely unfiltered.
			setBandPassFilter(directFilter0, directGain, directCutoff, lowFrequencyGain);
			AL10.alSourcei(sourceID, EFX10.AL_DIRECT_FILTER, directFilter0);
			return;
		}
		AL10.alSourcei(sourceID, EFX10.AL_DIRECT_FILTER, filters.direct);
	}

	private static void attachMutedSendFilters(int sourceID) {
		if (maxAuxSends >= 4) AL11.alSource3i(sourceID, EFX10.AL_AUXILIARY_SEND_FILTER, auxFXSlot0, 3, mutedSendFilter0);
		if (maxAuxSends >= 3) AL11.alSource3i(sourceID, EFX10.AL_AUXILIARY_SEND_FILTER, auxFXSlot1, 2, mutedSendFilter1);
		if (maxAuxSends >= 2) AL11.alSource3i(sourceID, EFX10.AL_AUXILIARY_SEND_FILTER, auxFXSlot2, 1, mutedSendFilter2);
		if (maxAuxSends >= 1) AL11.alSource3i(sourceID, EFX10.AL_AUXILIARY_SEND_FILTER, auxFXSlot3, 0, mutedSendFilter3);
	}

	private static void setBandPassFilter(int filter, float gain, float highFrequencyGain, float lowFrequencyGain) {
		EFX10.alFilterf(filter, EFX10.AL_BANDPASS_GAIN, MathHelper.clamp(gain, 0.0f, 1.0f));
		EFX10.alFilterf(filter, EFX10.AL_BANDPASS_GAINHF, MathHelper.clamp(highFrequencyGain, 0.0f, 1.0f));
		EFX10.alFilterf(filter, EFX10.AL_BANDPASS_GAINLF, MathHelper.clamp(lowFrequencyGain, 0.0f, 1.0f));
	}

	private record SourceEnvironment(Vec3d position, SoundCategory category, String name,
			ISound.AttenuationType attenuationType) {
	}

	private record EnvironmentParameters(float sendGain0, float sendGain1, float sendGain2, float sendGain3,
			float sendCutoff0, float sendCutoff1, float sendCutoff2, float sendCutoff3,
			float directCutoff, float directGain, float lowFrequencyGain,
			float listenerWetness, float airAbsorptionFactor) {
	}

	/**
	 * Quantized direct-filter parameters. 1 dB steps are far below the audible
	 * just-noticeable difference, so sources sharing a bucket sound identical
	 * through the direct path regardless of why their parameters agree.
	 */
	private record DirectFilterKey(int gainDb, int cutoffDb) {
		private static DirectFilterKey of(float gain, float cutoff) {
			return new DirectFilterKey(toDb(gain, 30), toDb(cutoff, 36));
		}

		private static int toDb(float value, int cap) {
			if (value >= 0.9999f) {
				return 0;
			}
			int db = (int) Math.round(-20.0 * Math.log10(Math.max(value, 1.0E-6f)));
			return Math.min(db, cap);
		}
	}

	private static final class SourceFilters {
		private int direct;
		// Dry (wetness-free) parameters are kept so the global listener water
		// coloration can rescale this filter in place when the listener's
		// wetness changes, without re-evaluating every attached source.
		private float dryGain;
		private float dryCutoff;
		private float lowFrequencyGain;
		private float wetness;
		private volatile long lastUsedNanos;

		private void set(float gain, float cutoff, float lowFrequencyGain, float wetness) {
			WaterAcoustics.Settings settings = Config.getWaterAcoustics();
			this.wetness = wetness;
			this.lowFrequencyGain = lowFrequencyGain;
			if (settings.listenerColorationEnabled()) {
				this.dryGain = gain / Math.max(listenerGain(settings.listenerGainLossDb(), wetness), 1.0E-6f);
				this.dryCutoff = cutoff / Math.max(listenerGain(settings.listenerHighFrequencyLossDb(), wetness), 1.0E-6f);
			} else {
				this.dryGain = gain;
				this.dryCutoff = cutoff;
			}
			setBandPassFilter(direct, gain, cutoff, lowFrequencyGain);
		}

		private void rescaleTo(float newWetness, WaterAcoustics.Settings settings) {
			if (!settings.listenerColorationEnabled() || Math.abs(newWetness - wetness) < 1.0E-4f) {
				return;
			}
			float gain = dryGain * listenerGain(settings.listenerGainLossDb(), newWetness);
			float cutoff = dryCutoff * listenerGain(settings.listenerHighFrequencyLossDb(), newWetness);
			lowFrequencyGain = listenerGain(settings.listenerLowFrequencyLossDb(), newWetness);
			wetness = newWetness;
			setBandPassFilter(direct, gain, cutoff, lowFrequencyGain);
		}

		private void markUsed() {
			lastUsedNanos = System.nanoTime();
		}

		private boolean valid() {
			return direct != 0;
		}

		private void delete() {
			if (direct != 0) EFX10.alDeleteFilters(direct);
		}
	}

	/**
	 * Applies the parameters in the enum ReverbParams to the main reverb
	 * effect.
	 */
	protected static void setReverbParams(final ReverbParams r, final int auxFXSlot, final int reverbSlot) {
		EFX10.alEffectf(reverbSlot, EFX10.AL_EAXREVERB_DENSITY, r.density);
		checkErrorLog("Error while assigning reverb density: " + r.density);

		EFX10.alEffectf(reverbSlot, EFX10.AL_EAXREVERB_DIFFUSION, r.diffusion);
		checkErrorLog("Error while assigning reverb diffusion: " + r.diffusion);

		EFX10.alEffectf(reverbSlot, EFX10.AL_EAXREVERB_GAIN, r.gain);
		checkErrorLog("Error while assigning reverb gain: " + r.gain);

		EFX10.alEffectf(reverbSlot, EFX10.AL_EAXREVERB_GAINHF, r.gainHF);
		checkErrorLog("Error while assigning reverb gainHF: " + r.gainHF);

		EFX10.alEffectf(reverbSlot, EFX10.AL_EAXREVERB_DECAY_TIME, r.decayTime);
		checkErrorLog("Error while assigning reverb decayTime: " + r.decayTime);

		EFX10.alEffectf(reverbSlot, EFX10.AL_EAXREVERB_DECAY_HFRATIO, r.decayHFRatio);
		checkErrorLog("Error while assigning reverb decayHFRatio: " + r.decayHFRatio);

		EFX10.alEffectf(reverbSlot, EFX10.AL_EAXREVERB_REFLECTIONS_GAIN, r.reflectionsGain);
		checkErrorLog("Error while assigning reverb reflectionsGain: " + r.reflectionsGain);

		EFX10.alEffectf(reverbSlot, EFX10.AL_EAXREVERB_LATE_REVERB_GAIN, r.lateReverbGain);
		checkErrorLog("Error while assigning reverb lateReverbGain: " + r.lateReverbGain);

		EFX10.alEffectf(reverbSlot, EFX10.AL_EAXREVERB_LATE_REVERB_DELAY, r.lateReverbDelay);
		checkErrorLog("Error while assigning reverb lateReverbDelay: " + r.lateReverbDelay);

		EFX10.alEffectf(reverbSlot, EFX10.AL_EAXREVERB_AIR_ABSORPTION_GAINHF, r.airAbsorptionGainHF);
		checkErrorLog("Error while assigning reverb airAbsorptionGainHF: " + r.airAbsorptionGainHF);

		EFX10.alEffectf(reverbSlot, EFX10.AL_EAXREVERB_ROOM_ROLLOFF_FACTOR, r.roomRolloffFactor);
		checkErrorLog("Error while assigning reverb roomRolloffFactor: " + r.roomRolloffFactor);

		EFX10.alEffectf(reverbSlot, EFX10.AL_EAXREVERB_ECHO_TIME, r.echoTime);
		checkErrorLog("Error while assigning reverb echoTime: " + r.echoTime);

		EFX10.alEffectf(reverbSlot, EFX10.AL_EAXREVERB_ECHO_DEPTH, r.echoDepth);
		checkErrorLog("Error while assigning reverb echoDepth: " + r.echoDepth);

		// Attach updated effect object
		EFX10.alAuxiliaryEffectSloti(auxFXSlot, EFX10.AL_EFFECTSLOT_EFFECT, reverbSlot);
		checkErrorLog("Error while assigning reverb effect slot: " + reverbSlot);
	}

	public static void log(final String message) {
		logger.info(message);
	}

	public static void logError(final String errorMessage) {
		logger.error(errorMessage);
	}

	@SubscribeEvent
	public void registerSound(RegistryEvent.Register<SoundEvent> event) {
		event.getRegistry().register(CLICK);
	}

	protected static void checkErrorLog(final String errorMessage) {
		final int error = AL10.alGetError();
		if (error == AL10.AL_NO_ERROR) {
			return;
		}

		String errorName = switch (error) {
            case AL10.AL_INVALID_NAME -> "AL_INVALID_NAME";
            case AL10.AL_INVALID_ENUM -> "AL_INVALID_ENUM";
            case AL10.AL_INVALID_VALUE -> "AL_INVALID_VALUE";
            case AL10.AL_INVALID_OPERATION -> "AL_INVALID_OPERATION";
            case AL10.AL_OUT_OF_MEMORY -> "AL_OUT_OF_MEMORY";
            default -> Integer.toString(error);
        };

        logError(errorMessage + " OpenAL error " + errorName);
    }

}
