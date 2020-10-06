package com.sonicether.soundphysics;

import java.nio.file.Path;
import java.nio.IntBuffer;

import java.lang.reflect.Method;  

import java.util.regex.Pattern;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.openal.ALCCapabilities;

import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.entity.Entity;
import net.minecraft.util.Direction;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.client.audio.AudioStreamBuffer;
import net.minecraft.client.audio.SoundEngine;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.Sound;
import net.minecraft.client.audio.SoundSource;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.audio.ElytraSound;
import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

@Mod(SoundPhysics.modid)
public class SoundPhysics {

	public static final String modid = "soundphysics";

	public static final Logger logger = LogManager.getLogger(modid);

	private static final Pattern rainPattern = Pattern.compile(".*rain.*");
	private static final Pattern stepPattern = Pattern.compile(".*step.*");
	private static final Pattern blockPattern = Pattern.compile(".*block.*");
	private static final Pattern uiPattern = Pattern.compile(".*\\/ui\\/.*");
	private static final Pattern clickPattern = Pattern.compile(".*random.click.*");
	private static final Pattern noteBlockPattern = Pattern.compile(".*block.note.*");
	private static final Pattern betweenlandsPattern = Pattern.compile("thebetweenlands:sounds\\/rift_.*\\.ogg");
	private static final Pattern travelPattern = Pattern.compile(".*portal\\/travel*.*");
	private static final Pattern elytraPattern = Pattern.compile(".*elytra\\/elytra_loop*.*");

	public SoundPhysics() {
		log("Mod Constructor");
		//FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
		MinecraftForge.EVENT_BUS.register(SoundPhysics.class);
		ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.CONFIG_SPEC);
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

	private static Minecraft mc;
	private static SoundEngine sndEngine;

	// THESE VARIABLES ARE CONSTANTLY ACCESSED AND USED BY ASM INJECTED CODE! DO
	// NOT REMOVE!
	public static int attenuationModel = AL10.AL_INVERSE_DISTANCE_CLAMPED;
	public static float referenceDistance = Config.referenceDistance;
	public static float globalRolloffFactor = Config.rolloffFactor;
	public static float globalVolumeMultiplier0 = Config.globalVolumeMultiplier; // 0 is because of DS trying to read the value of the original name
	public static float globalReverbMultiplier = 0.7f * Config.globalReverbGain;
	public static double soundDistanceAllowance = Config.soundDistanceAllowance;

	public static void init(SoundEngine engine) {
		log("Sound Physics Init");
		mc = Minecraft.getInstance();
		sndEngine = engine;
		try {
			setupEFX();
		} catch (Throwable e) {
			logError("Failed to init EFX");
			logError(e.toString());
		}
	}

	public static void applyConfigChanges() {
		referenceDistance = Config.referenceDistance;
		globalRolloffFactor = Config.rolloffFactor;
		globalReverbMultiplier = 0.7f * Config.globalReverbGain;
		soundDistanceAllowance = Config.soundDistanceAllowance;
		globalVolumeMultiplier0 = Config.globalVolumeMultiplier;

		if (auxFXSlot0 != 0) {
			// Set the global reverb parameters and apply them to the effect and
			// effectslot
			setReverbParams(ReverbParams.getReverb0(), auxFXSlot0, reverb0);
			setReverbParams(ReverbParams.getReverb1(), auxFXSlot1, reverb1);
			setReverbParams(ReverbParams.getReverb2(), auxFXSlot2, reverb2);
			setReverbParams(ReverbParams.getReverb3(), auxFXSlot3, reverb3);
		}
	}

	// I tried to use an IntBuffer for the attributes to not move the createcontext here
	// but it doesn't work for fuckall reason
	public static long createALContext(long device, ALCCapabilities caps) {
		int[] attribs = null;
		if (caps.ALC_EXT_EFX) { // We need 4, the default is 2 and the hard max is 16
			attribs = new int[]{EXTEfx.ALC_MAX_AUXILIARY_SENDS, 8, 0};
		}
		return ALC10.alcCreateContext(device, attribs);
	}

	private static void setupEFX() {
		// Get current context and device
		final long currentContext = ALC10.alcGetCurrentContext();
		final long currentDevice = ALC10.alcGetContextsDevice(currentContext);

		if (ALC10.alcIsExtensionPresent(currentDevice, "ALC_EXT_EFX")) {
			log("EFX Extension recognized.");
		} else {
			logError("EFX Extension not found on current device. Aborting.");
			return;
		}

		int nbSends = ALC10.alcGetInteger(currentDevice, EXTEfx.ALC_MAX_AUXILIARY_SENDS);

		if (nbSends < 4) {
			logError("Not enough Aux Sends ("+String.valueOf(nbSends)+"). Aborting");
			return;
		}

		log(String.valueOf(nbSends)+" of aux sends");

		// Create auxiliary effect slots
		auxFXSlot0 = EXTEfx.alGenAuxiliaryEffectSlots();
		log("Aux slot " + auxFXSlot0 + " created");
		EXTEfx.alAuxiliaryEffectSloti(auxFXSlot0, EXTEfx.AL_EFFECTSLOT_AUXILIARY_SEND_AUTO, AL10.AL_TRUE);

		auxFXSlot1 = EXTEfx.alGenAuxiliaryEffectSlots();
		log("Aux slot " + auxFXSlot1 + " created");
		EXTEfx.alAuxiliaryEffectSloti(auxFXSlot1, EXTEfx.AL_EFFECTSLOT_AUXILIARY_SEND_AUTO, AL10.AL_TRUE);

		auxFXSlot2 = EXTEfx.alGenAuxiliaryEffectSlots();
		log("Aux slot " + auxFXSlot2 + " created");
		EXTEfx.alAuxiliaryEffectSloti(auxFXSlot2, EXTEfx.AL_EFFECTSLOT_AUXILIARY_SEND_AUTO, AL10.AL_TRUE);

		auxFXSlot3 = EXTEfx.alGenAuxiliaryEffectSlots();
		log("Aux slot " + auxFXSlot3 + " created");
		EXTEfx.alAuxiliaryEffectSloti(auxFXSlot3, EXTEfx.AL_EFFECTSLOT_AUXILIARY_SEND_AUTO, AL10.AL_TRUE);
		checkErrorLog("Failed creating auxiliary effect slots!");

		reverb0 = EXTEfx.alGenEffects();
		EXTEfx.alEffecti(reverb0, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_EAXREVERB);
		checkErrorLog("Failed creating reverb effect slot 0!");
		reverb1 = EXTEfx.alGenEffects();
		EXTEfx.alEffecti(reverb1, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_EAXREVERB);
		checkErrorLog("Failed creating reverb effect slot 1!");
		reverb2 = EXTEfx.alGenEffects();
		EXTEfx.alEffecti(reverb2, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_EAXREVERB);
		checkErrorLog("Failed creating reverb effect slot 2!");
		reverb3 = EXTEfx.alGenEffects();
		EXTEfx.alEffecti(reverb3, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_EAXREVERB);
		checkErrorLog("Failed creating reverb effect slot 3!");

		// Create filters
		directFilter0 = EXTEfx.alGenFilters();
		EXTEfx.alFilteri(directFilter0, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);

		sendFilter0 = EXTEfx.alGenFilters();
		EXTEfx.alFilteri(sendFilter0, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);

		sendFilter1 = EXTEfx.alGenFilters();
		EXTEfx.alFilteri(sendFilter1, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);

		sendFilter2 = EXTEfx.alGenFilters();
		EXTEfx.alFilteri(sendFilter2, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);

		sendFilter3 = EXTEfx.alGenFilters();
		EXTEfx.alFilteri(sendFilter3, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
		checkErrorLog("Error creating lowpass filters!");

		applyConfigChanges();
	}

	/**
	 * CALLED BY ASM INJECTED CODE!
	 */
	public static String getSoundName(final ISound sound) {
		Sound s = sound.getSound();
		if (s == null || s == SoundHandler.MISSING_SOUND) {
			return sound.getSoundLocation().getPath();
		} else {
			return sound.getSoundLocation().getPath()+"|"+s.getSoundLocation().getPath();
		}
	}

	/**
	 * CALLED BY ASM INJECTED CODE!
	 */
	/*public static void setLastSound(final ISound snd, final SoundCategory sc, final ResourceLocation soundRes, final ResourceLocation eventRes) {
		lastSoundName = eventRes.toString()+"|"+soundRes.getPath(); // Quick and dirty hack to check the event and sound name
		lastSoundCategory = getSoundCategory(sc,lastSoundName);
		lastSoundAtt = snd.getAttenuationType();
		if (snd instanceof MovingSound)               // Hacky fix until i properly do moving sounds (I'm currently thinking about how)
			lastSoundCategory = SoundCategory.MASTER; // because all (at least vanilla) moving sounds don't init their position when played
	}*/

	/**
	 * CALLED BY ASM INJECTED CODE!
	 */
	/*public static void setLastSound(final SoundCategory sc, final String soundName) {
		lastSoundCategory = sc;
		lastSoundName = soundName;
		lastSoundAtt = ISound.AttenuationType.LINEAR;
	}*/

	/**
	 * CALLED BY ASM INJECTED CODE!
	 */
	// For IC2
	/*public static void setLastSound(final int position, final String soundName) {
		lastSoundCategory = position == 0 ? SoundCategory.BLOCKS : SoundCategory.PLAYERS;
		lastSoundName = "ic2:"+soundName;
		lastSoundAtt = ISound.AttenuationType.LINEAR;
	}*/

	/**
	 * CALLED BY ASM INJECTED CODE!
	 */
	/*public static int ic2DistanceCheckHook(float i, float dst, float x1, float y1, float z1, float x2, float y2, float z2) {
		if (i >= dst ||
			(MathHelper.floor(x1) == MathHelper.floor(x2) &&
			MathHelper.floor(y1) == MathHelper.floor(y2) &&
			MathHelper.floor(z1) == MathHelper.floor(z2))) return 1;
		if (dst == i) return 0;
		return -1;
	}*/

	public static SoundCategory getSoundCategory(final ISound sound) {
		return getSoundCategory(sound,getSoundName(sound));
	}	

	public static SoundCategory getSoundCategory(final ISound sound, final String soundName) {
		SoundCategory sc = sound.getCategory();
		if (Config.noteBlockEnable && sc == SoundCategory.RECORDS && noteBlockPattern.matcher(soundName).matches()) {
			return SoundCategory.BLOCKS;
		} else {
			return sc;
		}
	}

	/**
	 * CALLED BY ASM INJECTED CODE!
	 */
	public static float applyGlobalVolumeMultiplier(final float volume, final ISound sound) {
		SoundCategory cat = getSoundCategory(sound);
		if (!Config.volumeMulOnlyAffected || !(mc.player == null || mc.world == null || sound.getAttenuationType() == ISound.AttenuationType.NONE ||
			sound.isGlobal() || cat == SoundCategory.RECORDS || cat == SoundCategory.MUSIC || cat == SoundCategory.RECORDS)) {
			return volume*globalVolumeMultiplier0;
		} else {
			return volume;
		}
	}

	/**
	 * CALLED BY ASM INJECTED CODE!
	 */
	// For sounds that get played normally
	/*public static void onPlaySound(final float posX, final float posY, final float posZ, final int sourceID) {
		onPlaySound(posX, posY, posZ, sourceID, lastSoundCategory, lastSoundName, lastSoundAtt);
	}*/

	/**
	 * CALLED BY ASM INJECTED CODE!
	 */
	// For sounds that get played normally
	public static void onPlaySound(final ISound snd, final SoundSource sndsrc) {
		if (snd.isGlobal() || snd instanceof ElytraSound) return;
		final String name = getSoundName(snd);
		onPlaySound(snd.getX(), snd.getY(), snd.getZ(), sndsrc.field_216441_b, getSoundCategory(snd, name), name, snd.getAttenuationType());
	}	

	/**
	 * CALLED BY ASM INJECTED CODE!
	 */
	// For sounds that get played using OpenAL directly or just not using the minecraft sound system
	/*public static void onPlaySoundAL(final float posX, final float posY, final float posZ, final int sourceID) {
		onPlaySound(posX, posY, posZ, sourceID, SoundCategory.MASTER, "openal", ISound.AttenuationType.LINEAR);
	}*/

	/**
	 * CALLED BY ASM INJECTED CODE!
	 */
	public static void onPlaySound(final float posX, final float posY, final float posZ, final int sourceID, SoundCategory soundCat, String soundName, ISound.AttenuationType attType) {
		//log(String.valueOf(posX)+" "+String.valueOf(posY)+" "+String.valueOf(posZ)+" - "+String.valueOf(sourceID)+" - "+soundCat.toString()+" - "+attType.toString()+" - "+soundName);
		evaluateEnvironment(sourceID, posX, posY, posZ, soundCat, soundName, attType);
	}

	/**
	 * CALLED BY ASM INJECTED CODE!
	 */
	public static AudioStreamBuffer onLoadSound(AudioStreamBuffer buff, ResourceLocation resource) {
		if (buff == null || buff.audioFormat.getChannels() == 1 || !Config.autoSteroDownmix) return buff;
		String filename = resource.getPath();			// This should never happen anyways because music and records are streamed
		if (mc == null || mc.player == null || mc.world == null /*|| lastSoundCategory == SoundCategory.RECORDS || lastSoundCategory == SoundCategory.MUSIC*/ ||
			uiPattern.matcher(filename).matches() || clickPattern.matcher(filename).matches() || betweenlandsPattern.matcher(filename).matches() ||
			travelPattern.matcher(filename).matches() || elytraPattern.matcher(filename).matches()) {
			if (Config.autoSteroDownmixLogging) log("Not converting sound '"+filename+"'("+buff.audioFormat.toString()+")");
			return buff;
		}
		AudioFormat orignalformat = buff.audioFormat;
		int bits = orignalformat.getSampleSizeInBits();
		boolean bigendian = orignalformat.isBigEndian();
		AudioFormat monoformat = new AudioFormat(orignalformat.getEncoding(), orignalformat.getSampleRate(), bits,
												1, orignalformat.getFrameSize(), orignalformat.getFrameRate(), bigendian);
		if (Config.autoSteroDownmixLogging) log("Converting sound '"+filename+"'("+orignalformat.toString()+") to mono ("+monoformat.toString()+")");

		ByteBuffer src = buff.field_216475_a;
		src.order(bigendian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
		src.rewind();
		int size = src.remaining();

		if (bits == 8) {
			for (int i = 0; i < size; i+=2) {
				src.put(i/2,(byte)((src.get(i)+src.get(i+1))/2));
			}
		} else if (bits == 16) {
			for (int i = 0; i < size; i+=4) {
				src.putShort((i/2),(short)((src.getShort(i)+src.getShort(i+2))/2));
			}
		}
		
		src.flip();

		buff.audioFormat = monoformat;
		return buff;
	}

	/**
	 * CALLED BY ASM INJECTED CODE!
	 */
	public static double calculateEntitySoundOffset(final Entity entity, final SoundEvent sound) {
		if (sound == null) return entity.getEyeHeight();
		if (stepPattern.matcher(sound.getName().getPath()).matches()) {
			return 0;
		}

		return entity.getEyeHeight();
	}

	/**
	 * CALLED BY ASM INJECTED CODE!
	 */
	/*public static Vec3d computronicsOffset(Vec3d or, TileEntity te, PropertyDirection pd) {
		if (!te.hasWorld()) return or;
		EnumFacing ef = te.getWorld().getBlockState(te.getPos()).getValue(pd);
		Vec3d efv = getNormalFromFacing(ef).scale(0.51);
		return or.add(efv);
	}*/

	// Copy of isRainingAt
	/*private static boolean isSnowingAt(BlockPos position, boolean check_rain) {
		if ((check_rain && !mc.world.isRaining()) || !mc.world.canSeeSky(position) ||
			mc.world.getPrecipitationHeight(position).getY() > position.getY()) {
			return false;
		} else {
			return mc.world.canSnowAt(position, false) || mc.world.getBiome(position).getEnableSnow();
		}
	}*/

	// Copy of isRainingAt
	public static boolean isSnowingAt(BlockPos position, boolean check_rain) {
		if (check_rain && !mc.world.isRaining()) {
			return false;
		} else if (!mc.world.canSeeSky(position)) {
			return false;
		} else if (mc.world.getHeight(Heightmap.Type.MOTION_BLOCKING, position).getY() > position.getY()) {
			return false;
		} else {
			return mc.world.getBiome(position).getPrecipitation() == Biome.RainType.SNOW;
		}
	}

	@SuppressWarnings("deprecation")
	private static float getBlockReflectivity(final BlockPos blockPos) {
		final Block block = mc.world.getBlockState(blockPos).getBlock();
		final SoundType soundType = block.getSoundType(null); // The argument is unused

		float reflectivity = 0.5f;

		if (soundType == SoundType.STONE) {
			reflectivity = Config.stoneReflectivity;
		} else if (soundType == SoundType.WOOD || soundType == SoundType.SCAFFOLDING) {
			reflectivity = Config.woodReflectivity;
		} else if (soundType == SoundType.GROUND) {
			reflectivity = Config.groundReflectivity;
		} else if (soundType == SoundType.PLANT || soundType == SoundType.WET_GRASS || soundType == SoundType.CORAL ||
				   soundType == SoundType.BAMBOO || soundType == SoundType.BAMBOO_SAPLING || soundType == SoundType.CROP ||
				   soundType == SoundType.STEM || soundType == SoundType.NETHER_WART || soundType == SoundType.SWEET_BERRY_BUSH) {
			reflectivity = Config.plantReflectivity;
		} else if (soundType == SoundType.METAL || soundType == SoundType.LANTERN) {
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
		} else if (soundType == SoundType.SLIME || soundType == SoundType.field_226947_m_) { // HONEY_BLOCK
			reflectivity = Config.slimeReflectivity;
		}

		reflectivity *= Config.globalBlockReflectance;

		return reflectivity;
	}

	private static Vec3d getNormalFromFacing(final Direction sideHit) {
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
			final Vec3d playerPos, final String name, final SoundCategory category) {
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
			(name == "openal" && !mc.world.isAirBlock(new BlockPos(soundX,soundY,soundZ)))) &&
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

	/*private static float getPlayerEyeHeight() throws IllegalStateException {
		ReentrantReadWriteLock lock = (ReentrantReadWriteLock)mc.player.getDataManager().lock;
		if (lock.isWriteLocked()) {
			logError("Deadlock detected, avoiding it by throwing exception");
			throw new IllegalStateException("Player's Data Mananger is write locked");
		}
		return mc.player.getEyeHeight();
	}*/

	private static BlockRayTraceResult rayTraceBlocks(Vec3d start, Vec3d stop, boolean stopOnLiquid) {
		return mc.world.rayTraceBlocks(new RayTraceContext(start, stop, RayTraceContext.BlockMode.COLLIDER,
										stopOnLiquid ? RayTraceContext.FluidMode.ANY : RayTraceContext.FluidMode.NONE, mc.player));
	}

	@SuppressWarnings("deprecation")
	private static void evaluateEnvironment(final int sourceID, final float posX, final float posY, final float posZ, final SoundCategory category,
											final String name, ISound.AttenuationType attType) {
		try {
			if (mc.player == null || mc.world == null || category == SoundCategory.MASTER || attType == ISound.AttenuationType.NONE ||
				 category == SoundCategory.RECORDS || category == SoundCategory.MUSIC) {
				// posY <= 0 as a condition has to be there: Ingame
				// menu clicks do have a player and world present
				// The Y position check has been removed due to problems with Cubic Chunks
				setEnvironment(sourceID, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f);
				return;
			}

			final boolean isRain = category == SoundCategory.WEATHER; //rainPattern.matcher(name).matches();

			if (Config.skipRainOcclusionTracing && isRain) {
				setEnvironment(sourceID, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f);
				return;
			}

			float directCutoff = 1.0f;
			final float absorptionCoeff = Config.globalBlockAbsorption * 3.0f;

			//final Vec3d playerPos = new Vec3d(mc.player.getPosX(), mc.player.getPosY() + getPlayerEyeHeight(), mc.player.getPosZ());
			final Vec3d playerPos = new Vec3d(mc.player.getPosX(), mc.player.getPosY() + mc.player.getEyeHeight(), mc.player.getPosZ());
			final Vec3d soundPos = offsetSoundByName(posX, posY, posZ, playerPos, name, category);
			final Vec3d normalToPlayer = playerPos.subtract(soundPos).normalize();

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

			for (int i = 0; i < 10; i++) {
				final BlockRayTraceResult rayHit = rayTraceBlocks(rayOrigin, playerPos, true);

				if (rayHit.getType() == RayTraceResult.Type.MISS) {
					break;
				}

				final BlockState blockHit = mc.world.getBlockState(rayHit.getPos());

				float blockOcclusion = 1.0f;

				if (!blockHit.isOpaqueCube(mc.world, rayHit.getPos())) {
					// log("not a solid block!");
					blockOcclusion *= 0.15f;
				}

				occlusionAccumulation += blockOcclusion;

				Vec3d hitVec = rayHit.getHitVec();

				rayOrigin = new Vec3d(hitVec.x + normalToPlayer.x * 0.1, hitVec.y + normalToPlayer.y * 0.1,
						hitVec.z + normalToPlayer.z * 0.1);
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

			if (mc.player.canSwim()) { // canSwim also checks if the head is in the water
				directCutoff *= 1.0f - Config.underwaterFilter;
			}

			if (isRain) {
				setEnvironment(sourceID, sendGain0, sendGain1, sendGain2, sendGain3, sendCutoff0, sendCutoff1, sendCutoff2,
						sendCutoff3, directCutoff, directGain, airAbsorptionFactor);
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
				final float fi = i;
				final float fiN = fi / numRays;
				final float longitude = gAngle * fi;
				final float latitude = (float) Math.asin(fiN * 2.0f - 1.0f);

				final Vec3d rayDir = new Vec3d(Math.cos(latitude) * Math.cos(longitude),
						Math.cos(latitude) * Math.sin(longitude), Math.sin(latitude));

				final Vec3d rayStart = new Vec3d(soundPos.x, soundPos.y, soundPos.z);

				final Vec3d rayEnd = new Vec3d(rayStart.x + rayDir.x * maxDistance, rayStart.y + rayDir.y * maxDistance,
						rayStart.z + rayDir.z * maxDistance);

				final BlockRayTraceResult rayHit = rayTraceBlocks(rayStart, rayEnd, true);

				if (rayHit.getType() != RayTraceResult.Type.MISS) {
					// Additional bounces
					BlockPos lastHitBlock = rayHit.getPos();
					Vec3d lastHitPos = rayHit.getHitVec();
					Vec3d lastHitNormal = getNormalFromFacing(rayHit.getFace());
					Vec3d lastRayDir = rayDir;

					final double rayLength = soundPos.distanceTo(lastHitPos);

					float totalRayDistance = (float) rayLength;

					// Secondary ray bounces
					for (int j = 0; j < rayBounces; j++) {
						final Vec3d newRayDir = reflect(lastRayDir, lastHitNormal);
						// Vec3d newRayDir = lastHitNormal;
						final Vec3d newRayStart = new Vec3d(lastHitPos.x + lastHitNormal.x * 0.01,
								lastHitPos.y + lastHitNormal.y * 0.01, lastHitPos.z + lastHitNormal.z * 0.01);
						final Vec3d newRayEnd = new Vec3d(newRayStart.x + newRayDir.x * maxDistance,
								newRayStart.y + newRayDir.y * maxDistance, newRayStart.z + newRayDir.z * maxDistance);

						final BlockRayTraceResult newRayHit = rayTraceBlocks(newRayStart, newRayEnd, true);

						float energyTowardsPlayer = 0.25f;
						final float blockReflectivity = getBlockReflectivity(lastHitBlock);
						energyTowardsPlayer *= blockReflectivity * 0.75f + 0.25f;

						if (newRayHit.getType() == RayTraceResult.Type.MISS) {
							totalRayDistance += lastHitPos.distanceTo(playerPos);
						} else {
							lastHitPos = newRayHit.getHitVec();
							lastHitNormal = getNormalFromFacing(newRayHit.getFace());
							lastRayDir = newRayDir;
							lastHitBlock = newRayHit.getPos();

							final double newRayLength = lastHitPos.distanceTo(lastHitPos);

							totalRayDistance += newRayLength;

							bounceReflectivityRatio[j] += blockReflectivity;

							// Cast one final ray towards the player. If it's
							// unobstructed, then the sound source and the player
							// share airspace.
							if (Config.simplerSharedAirspaceSimulation && j == rayBounces - 1
									|| !Config.simplerSharedAirspaceSimulation) {
								final Vec3d finalRayStart = new Vec3d(lastHitPos.x + lastHitNormal.x * 0.01,
										lastHitPos.y + lastHitNormal.y * 0.01, lastHitPos.z + lastHitNormal.z * 0.01);

								final BlockRayTraceResult finalRayHit = rayTraceBlocks(finalRayStart, playerPos, true);

								if (finalRayHit.getType() == RayTraceResult.Type.MISS) {
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
						if (newRayHit.getType() == RayTraceResult.Type.MISS) {
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

			if (mc.player.isInWater()) {
				sendCutoff0 *= 0.4f;
				sendCutoff1 *= 0.4f;
				sendCutoff2 *= 0.4f;
				sendCutoff3 *= 0.4f;
			}

			/*if (Config.midnightPatching && Config.midnightPatchingFix && mc.world.provider.getDimensionType().getName() == "midnight") {
				// Since the patch removes the incompatble reverb, readd some reverb everywhere
				// It's not a great fix but it works fine
				sendGain1 = MathHelper.clamp(sendGain1, 0.3f, 1.0f);
				sendGain2 = MathHelper.clamp(sendGain2, 0.5f, 1.0f);
				sendGain3 = MathHelper.clamp(sendGain3, 0.7f, 1.0f);
				sendCutoff1 = MathHelper.clamp(sendCutoff1, 0.3f, 1.0f);
				sendCutoff2 = MathHelper.clamp(sendCutoff2, 0.5f, 1.0f);
				sendCutoff3 = MathHelper.clamp(sendCutoff3, 0.7f, 1.0f);
			}*/

			setEnvironment(sourceID, sendGain0, sendGain1, sendGain2, sendGain3, sendCutoff0, sendCutoff1, sendCutoff2,
					sendCutoff3, directCutoff, directGain, airAbsorptionFactor);
		} catch(Exception e) {
			logger.error("Error while evaluation environment:", e);
			setEnvironment(sourceID, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f);
		}
	}

	private static void setEnvironment(final int sourceID, final float sendGain0, final float sendGain1,
			final float sendGain2, final float sendGain3, final float sendCutoff0, final float sendCutoff1,
			final float sendCutoff2, final float sendCutoff3, final float directCutoff, final float directGain,
			final float airAbsorptionFactor) {
		// Set reverb send filter values and set source to send to all reverb fx
		// slots
		EXTEfx.alFilterf(sendFilter0, EXTEfx.AL_LOWPASS_GAIN, sendGain0);
		EXTEfx.alFilterf(sendFilter0, EXTEfx.AL_LOWPASS_GAINHF, sendCutoff0);
		AL11.alSource3i(sourceID, EXTEfx.AL_AUXILIARY_SEND_FILTER, auxFXSlot0, 0, sendFilter0);

		EXTEfx.alFilterf(sendFilter1, EXTEfx.AL_LOWPASS_GAIN, sendGain1);
		EXTEfx.alFilterf(sendFilter1, EXTEfx.AL_LOWPASS_GAINHF, sendCutoff1);
		AL11.alSource3i(sourceID, EXTEfx.AL_AUXILIARY_SEND_FILTER, auxFXSlot1, 1, sendFilter1);

		EXTEfx.alFilterf(sendFilter2, EXTEfx.AL_LOWPASS_GAIN, sendGain2);
		EXTEfx.alFilterf(sendFilter2, EXTEfx.AL_LOWPASS_GAINHF, sendCutoff2);
		AL11.alSource3i(sourceID, EXTEfx.AL_AUXILIARY_SEND_FILTER, auxFXSlot2, 2, sendFilter2);

		EXTEfx.alFilterf(sendFilter3, EXTEfx.AL_LOWPASS_GAIN, sendGain3);
		EXTEfx.alFilterf(sendFilter3, EXTEfx.AL_LOWPASS_GAINHF, sendCutoff3);
		AL11.alSource3i(sourceID, EXTEfx.AL_AUXILIARY_SEND_FILTER, auxFXSlot3, 3, sendFilter3);

		EXTEfx.alFilterf(directFilter0, EXTEfx.AL_LOWPASS_GAIN, directGain);
		EXTEfx.alFilterf(directFilter0, EXTEfx.AL_LOWPASS_GAINHF, directCutoff);
		AL10.alSourcei(sourceID, EXTEfx.AL_DIRECT_FILTER, directFilter0);

		AL10.alSourcef(sourceID, EXTEfx.AL_AIR_ABSORPTION_FACTOR, MathHelper.clamp(Config.airAbsorption * airAbsorptionFactor,0.0f,10.0f));
		checkErrorLog("Error while setting environment for source: " + sourceID);
	}

	/**
	 * Applies the parameters in the enum ReverbParams to the main reverb
	 * effect.
	 */
	protected static void setReverbParams(final ReverbParams r, final int auxFXSlot, final int reverbSlot) {
		EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_DENSITY, r.density);
		checkErrorLog("Error while assigning reverb density: " + r.density);

		EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_DIFFUSION, r.diffusion);
		checkErrorLog("Error while assigning reverb diffusion: " + r.diffusion);

		EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_GAIN, r.gain);
		checkErrorLog("Error while assigning reverb gain: " + r.gain);

		EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_GAINHF, r.gainHF);
		checkErrorLog("Error while assigning reverb gainHF: " + r.gainHF);

		EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_DECAY_TIME, r.decayTime);
		checkErrorLog("Error while assigning reverb decayTime: " + r.decayTime);

		EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_DECAY_HFRATIO, r.decayHFRatio);
		checkErrorLog("Error while assigning reverb decayHFRatio: " + r.decayHFRatio);

		EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_REFLECTIONS_GAIN, r.reflectionsGain);
		checkErrorLog("Error while assigning reverb reflectionsGain: " + r.reflectionsGain);

		EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_LATE_REVERB_GAIN, r.lateReverbGain);
		checkErrorLog("Error while assigning reverb lateReverbGain: " + r.lateReverbGain);

		EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_LATE_REVERB_DELAY, r.lateReverbDelay);
		checkErrorLog("Error while assigning reverb lateReverbDelay: " + r.lateReverbDelay);

		EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_AIR_ABSORPTION_GAINHF, r.airAbsorptionGainHF);
		checkErrorLog("Error while assigning reverb airAbsorptionGainHF: " + r.airAbsorptionGainHF);

		EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_ROOM_ROLLOFF_FACTOR, r.roomRolloffFactor);
		checkErrorLog("Error while assigning reverb roomRolloffFactor: " + r.roomRolloffFactor);

		EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_ECHO_TIME, r.echoTime);
		checkErrorLog("Error while assigning reverb echoTime: " + r.echoTime);

		EXTEfx.alEffectf(reverbSlot, EXTEfx.AL_EAXREVERB_ECHO_DEPTH, r.echoDepth);
		checkErrorLog("Error while assigning reverb echoDepth: " + r.echoDepth);

		// Attach updated effect object
		EXTEfx.alAuxiliaryEffectSloti(auxFXSlot, EXTEfx.AL_EFFECTSLOT_EFFECT, reverbSlot);
		checkErrorLog("Error while assigning reverb effect slot: " + reverbSlot);
	}

	public static void log(final String message) {
		logger.info(message);
	}

	public static void logError(final String errorMessage) {
		logger.error(errorMessage);
	}

	protected static boolean checkErrorLog(final String errorMessage) {
		final int error = AL10.alGetError();
		if (error == AL10.AL_NO_ERROR) {
			return false;
		}

		String errorName;

		switch (error) {
			case AL10.AL_INVALID_NAME:
				errorName = "AL_INVALID_NAME";
				break;
			case AL10.AL_INVALID_ENUM:
				errorName = "AL_INVALID_ENUM";
				break;
			case AL10.AL_INVALID_VALUE:
				errorName = "AL_INVALID_VALUE";
				break;
			case AL10.AL_INVALID_OPERATION:
				errorName = "AL_INVALID_OPERATION";
				break;
			case AL10.AL_OUT_OF_MEMORY:
				errorName = "AL_OUT_OF_MEMORY";
				break;
			default:
				errorName = Integer.toString(error);
				break;
		}

		logError(errorMessage + " OpenAL error " + errorName);
		return true;
	}

}
