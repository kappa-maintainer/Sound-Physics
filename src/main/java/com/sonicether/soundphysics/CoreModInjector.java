package com.sonicether.soundphysics;

import java.util.Map;
import java.util.Iterator;
import java.util.ListIterator;

import java.io.StringWriter;
import java.io.PrintWriter;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.FrameNode;

import org.objectweb.asm.util.TraceMethodVisitor;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import net.minecraft.launchwrapper.IClassTransformer;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

/**
 * Keep as reference, won't use in actual logic
 */
public class CoreModInjector implements IClassTransformer {

    public static final Logger logger = LogManager.getLogger(SoundPhysics.modid+"injector");

    private static boolean shouldPatchDS(boolean checkNew) {
        if (Loader.isModLoaded("dsurround")) {
            Map<String,ModContainer> mods = Loader.instance().getIndexedModList();
            String[] version = mods.get("dsurround").getVersion().split("\\.");
            if (version.length < 2) {
                logError("What the hell, DS's version is not properly formatted ?");
            } else return (!checkNew && version[1].equals("5")) || (checkNew && version[1].equals("6"));
        }
        return false;
    }

    private static boolean isIC2Classic() {
        if (Loader.isModLoaded("ic2")) {
            Map<String,ModContainer> mods = Loader.instance().getIndexedModList();
            String version = mods.get("ic2").getVersion();
            return !version.endsWith("ex112");
        }
        return false;
    }

    @Override
    public byte[] transform(final String obfuscated, final String deobfuscated, byte[] bytes) {
        if (obfuscated.equals("chm$a")) {
            // Inside SoundManager.SoundSystemStarterThread
            InsnList toInject = new InsnList();
            toInject.add(new VarInsnNode(Opcodes.ALOAD, 0));
            toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/sonicether/soundphysics/SoundPhysics", "init",
                "(Lpaulscode/sound/SoundSystem;)V", false));

            // Target method: Constructor
            bytes = patchMethodInClass(obfuscated, bytes, "<init>", "(Lchm;)V", Opcodes.INVOKESPECIAL,
                AbstractInsnNode.METHOD_INSN, "<init>", null, -1, toInject, false, 0, 0, false, 0, -1);
        } else

        if (obfuscated.equals("chm")) {
            // Inside SoundManager
            InsnList toInject = new InsnList();
            toInject.add(new VarInsnNode(Opcodes.ALOAD, 1));
            toInject.add(new VarInsnNode(Opcodes.ALOAD, 7));
            toInject.add(new VarInsnNode(Opcodes.ALOAD, 4));
            toInject.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "cgq", "a", "()Lnf;", false));
            toInject.add(new VarInsnNode(Opcodes.ALOAD, 3));
            toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/sonicether/soundphysics/SoundPhysics",
                "setLastSound", "(Lcgt;Lqg;Lnf;Lnf;)V", false));

            // Target method: playSound
            bytes = patchMethodInClass(obfuscated, bytes, "c", "(Lcgt;)V", Opcodes.INVOKEVIRTUAL,
                AbstractInsnNode.METHOD_INSN, "setPitch", null, -1, toInject, false, 0, 0, false, 0, -1);

            toInject = new InsnList();
            toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/sonicether/soundphysics/SoundPhysics",
                "applyGlobalVolumeMultiplier", "(F)F", false));

            // Target method: playSound, target invocation setVolume
            bytes = patchMethodInClass(obfuscated, bytes, "c", "(Lcgt;)V", Opcodes.INVOKEVIRTUAL,
                AbstractInsnNode.METHOD_INSN, "setVolume", null, -1, toInject, true, 0, 0, false, 0, -1);
        } else

        if (obfuscated.equals("paulscode.sound.libraries.SourceLWJGLOpenAL") ||
            (obfuscated.equals("ovr.paulscode.sound.libraries.SourceLWJGLOpenAL") && Config.glibyVCPatching)) {
            // Inside SourceLWJGLOpenAL
            InsnList toInject = new InsnList();

            final String classPath = obfuscated.replace(".","/");
            String channelPath = "paulscode/sound/libraries/ChannelLWJGLOpenAL";
            if (obfuscated.equals("ovr.paulscode.sound.libraries.SourceLWJGLOpenAL"))
                channelPath = "ovr/"+channelPath;

            toInject.add(new VarInsnNode(Opcodes.ALOAD, 0));
            toInject.add(new FieldInsnNode(Opcodes.GETFIELD, classPath, "position",
                "Lpaulscode/sound/Vector3D;"));
            toInject.add(new FieldInsnNode(Opcodes.GETFIELD, "paulscode/sound/Vector3D", "x", "F"));
            toInject.add(new VarInsnNode(Opcodes.ALOAD, 0));
            toInject.add(new FieldInsnNode(Opcodes.GETFIELD, classPath, "position",
                "Lpaulscode/sound/Vector3D;"));
            toInject.add(new FieldInsnNode(Opcodes.GETFIELD, "paulscode/sound/Vector3D", "y", "F"));
            toInject.add(new VarInsnNode(Opcodes.ALOAD, 0));
            toInject.add(new FieldInsnNode(Opcodes.GETFIELD, classPath, "position",
                "Lpaulscode/sound/Vector3D;"));
            toInject.add(new FieldInsnNode(Opcodes.GETFIELD, "paulscode/sound/Vector3D", "z", "F"));
            toInject.add(new VarInsnNode(Opcodes.ALOAD, 0));
            toInject.add(new FieldInsnNode(Opcodes.GETFIELD, classPath,
                "channelOpenAL", "L"+channelPath+";"));
            toInject.add(new FieldInsnNode(Opcodes.GETFIELD, channelPath, "ALSource",
                "Ljava/nio/IntBuffer;"));
            toInject.add(new InsnNode(Opcodes.ICONST_0));
            toInject.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/nio/IntBuffer", "get", "(I)I", false));
            toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/sonicether/soundphysics/SoundPhysics",
                "onPlaySound", "(FFFI)V", false));

            // Target method: play
            bytes = patchMethodInClass(obfuscated, bytes, "play", "(Lpaulscode/sound/Channel;)V", Opcodes.INVOKEVIRTUAL,
                AbstractInsnNode.METHOD_INSN, "play", null, -1, toInject, false, 0, 0, false, 0, -1);
        } else

            // Convert stero sounds to mono
            if ((obfuscated.equals("paulscode.sound.libraries.LibraryLWJGLOpenAL") ||
                (obfuscated.equals("ovr.paulscode.sound.libraries.LibraryLWJGLOpenAL") && Config.glibyVCPatching)) && Config.autoSteroDownmix) {
                // Inside LibraryLWJGLOpenAL
                InsnList toInject = new InsnList();

                toInject.add(new VarInsnNode(Opcodes.ALOAD, 4));
                toInject.add(new VarInsnNode(Opcodes.ALOAD, 1));
                toInject.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "paulscode/sound/FilenameURL", "getFilename", "()Ljava/lang/String;", false));

                toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/sonicether/soundphysics/SoundPhysics",
                    "onLoadSound", "(Lpaulscode/sound/SoundBuffer;Ljava/lang/String;)Lpaulscode/sound/SoundBuffer;", false));

                toInject.add(new VarInsnNode(Opcodes.ASTORE, 4));
                //buffer = onLoadSound(SoundPhysics.buffer,filenameURL.getFilename());

                // Target method: loadSound 
                bytes = patchMethodInClass(obfuscated, bytes, "loadSound", "(Lpaulscode/sound/FilenameURL;)Z", Opcodes.INVOKEINTERFACE,
                    AbstractInsnNode.METHOD_INSN, "cleanup", null, -1, toInject, false, 0, 0, false, 0, -1);

                toInject = new InsnList();

                toInject.add(new VarInsnNode(Opcodes.ALOAD, 2));

                toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/sonicether/soundphysics/SoundPhysics",
                    "onLoadSound", "(Lpaulscode/sound/SoundBuffer;Ljava/lang/String;)Lpaulscode/sound/SoundBuffer;", false));

                toInject.add(new VarInsnNode(Opcodes.ASTORE, 1));
                toInject.add(new VarInsnNode(Opcodes.ALOAD, 1));

                // Target method: loadSound 
                bytes = patchMethodInClass(obfuscated, bytes, "loadSound", "(Lpaulscode/sound/SoundBuffer;Ljava/lang/String;)Z", Opcodes.INVOKEVIRTUAL,
                    AbstractInsnNode.METHOD_INSN, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", -1, toInject, true, 0, 0, false, 0, 0);
            } else

            if (obfuscated.equals("paulscode.sound.SoundSystem")) {
                // Inside SoundSystem
                InsnList toInject = new InsnList();

                toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
                    "attenuationModel", "I"));
                toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
                    "globalRolloffFactor", "F"));

                // Target method: newSource
                bytes = patchMethodInClass(obfuscated, bytes, "newSource",
                    "(ZLjava/lang/String;Ljava/net/URL;Ljava/lang/String;ZFFFIF)V", Opcodes.INVOKESPECIAL,
                    AbstractInsnNode.METHOD_INSN, "<init>", null, -1, toInject, true, 2, 0, false, 0, -1);

                // Can't reuse the list for some reason
                toInject = new InsnList();

                toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
                    "attenuationModel", "I"));
                toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
                    "globalRolloffFactor", "F"));

                // Target method: newSource
                bytes = patchMethodInClass(obfuscated, bytes, "newSource",
                    "(ZLjava/lang/String;Ljava/lang/String;ZFFFIF)V", Opcodes.INVOKESPECIAL,
                    AbstractInsnNode.METHOD_INSN, "<init>", null, -1, toInject, true, 2, 0, false, 0, -1);
            } else

            if (obfuscated.equals("pl")) {
                // Inside PlayerList
                InsnList toInject = new InsnList();

                // Multiply sound distance volume play decision by
                // SoundPhysics.soundDistanceAllowance
                toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
                    "soundDistanceAllowance", "D"));
                toInject.add(new InsnNode(Opcodes.DMUL));

                // Target method: sendToAllNearExcept
                bytes = patchMethodInClass(obfuscated, bytes, "a", "(Laed;DDDDILht;)V", Opcodes.DCMPG,
                    AbstractInsnNode.INSN, "", "", -1, toInject, true, 0, 0, false, 0, -1);
            } else

            if (obfuscated.equals("vg")) {
                // Inside Entity
                InsnList toInject = new InsnList();

                // Offset entity sound by their eye height
                toInject.add(new VarInsnNode(Opcodes.ALOAD, 1));
                toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/sonicether/soundphysics/SoundPhysics",
                    "calculateEntitySoundOffset", "(Lvg;Lqe;)D", false));
                toInject.add(new InsnNode(Opcodes.DADD));
                toInject.add(new VarInsnNode(Opcodes.ALOAD, 0));

                // Target method: playSound
                // Inside target method, target node: Entity/getSoundCategory
                bytes = patchMethodInClass(obfuscated, bytes, "a", "(Lqe;FF)V", Opcodes.INVOKEVIRTUAL,
                    AbstractInsnNode.METHOD_INSN, "bK", null, -1, toInject, true, 0, 0, false, -3, -1);
            } else

                // Fix for computronics's devices
                if (obfuscated.equals("pl.asie.lib.audio.StreamingAudioPlayer") && Config.computronicsPatching) {
                    // Inside StreamingAudioPlayer
                    InsnList toInject = new InsnList();

                    toInject.add(new VarInsnNode(Opcodes.FLOAD, 2));
                    toInject.add(new VarInsnNode(Opcodes.FLOAD, 3));
                    toInject.add(new VarInsnNode(Opcodes.FLOAD, 4));
                    toInject.add(new VarInsnNode(Opcodes.ALOAD, 8));
                    toInject.add(new FieldInsnNode(Opcodes.GETFIELD, "pl/asie/lib/audio/StreamingAudioPlayer$SourceEntry", "src",
                        "Ljava/nio/IntBuffer;"));
                    toInject.add(new InsnNode(Opcodes.ICONST_0));
                    toInject.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/nio/IntBuffer", "get", "(I)I", false));

                    toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/sonicether/soundphysics/SoundPhysics",
                        "onPlaySoundAL", "(FFFI)V", false));

                    // Target method: play 
                    bytes = patchMethodInClass(obfuscated, bytes, "play", "(Ljava/lang/String;FFFF)V", Opcodes.INVOKESTATIC,
                        AbstractInsnNode.METHOD_INSN, "alSourceQueueBuffers", null, -1, toInject, true, 0, 0, false, -5, -1);

                    toInject = new InsnList();

                    toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
                        "soundDistanceAllowance", "D"));
                    toInject.add(new InsnNode(Opcodes.D2F));
                    toInject.add(new InsnNode(Opcodes.FMUL));

                    // Target method: setHearing
                    bytes = patchMethodInClass(obfuscated, bytes, "setHearing", "(FF)V", Opcodes.FLOAD,
                        AbstractInsnNode.VAR_INSN, "", null, 1, toInject, false, 0, 0, false, 0, -1);
                } else

                if (obfuscated.equals("pl.asie.computronics.api.audio.AudioPacket") && Config.computronicsPatching) {
                    // Inside AudioPacket
                    InsnList toInject = new InsnList();

                    // This probably should only get multipled once, i don't know why i do it twice
                    toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
                        "soundDistanceAllowance", "D"));
                    toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
                        "soundDistanceAllowance", "D"));
                    toInject.add(new InsnNode(Opcodes.DMUL));
                    toInject.add(new InsnNode(Opcodes.D2I));
                    toInject.add(new InsnNode(Opcodes.IMUL));

                    // Target method: canHearReceiver
                    bytes = patchMethodInClass(obfuscated, bytes, "canHearReceiver", "(Lnet/minecraft/entity/player/EntityPlayerMP;Lpl/asie/computronics/api/audio/IAudioReceiver;)Z", Opcodes.IMUL,
                        AbstractInsnNode.INSN, "", null, -1, toInject, false, 0, 0, false, 0, -1);
                } else

                if (obfuscated.equals("pl.asie.computronics.tile.TileTapeDrive$1") && Config.computronicsPatching) {
                    // Inside TileTapeDrive.internalSpeaker
                    InsnList toInject = new InsnList();

                    toInject.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    toInject.add(new FieldInsnNode(Opcodes.GETFIELD, "pl/asie/computronics/tile/TileTapeDrive$1", "this$0",
                        "Lpl/asie/computronics/tile/TileTapeDrive;"));
                    toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "pl/asie/computronics/Computronics",
                        "tapeReader", "Lpl/asie/computronics/block/BlockTapeReader;"));
                    toInject.add(new FieldInsnNode(Opcodes.GETFIELD, "pl/asie/computronics/block/BlockTapeReader", "rotation",
                        "Lpl/asie/lib/block/BlockBase$Rotation;"));
                    toInject.add(new FieldInsnNode(Opcodes.GETFIELD, "pl/asie/lib/block/BlockBase$Rotation", "FACING",
                        "Lnet/minecraft/block/properties/PropertyDirection;"));
                    toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/sonicether/soundphysics/SoundPhysics", "computronicsOffset",
                        "(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/tileentity/TileEntity;Lnet/minecraft/block/properties/PropertyDirection;)Lnet/minecraft/util/math/Vec3d;", false));

                    // Target method: getSoundPos
                    bytes = patchMethodInClass(obfuscated, bytes, "getSoundPos", "()Lnet/minecraft/util/math/Vec3d;", Opcodes.ARETURN,
                        AbstractInsnNode.INSN, "", null, -1, toInject, true, 0, 0, false, 0, -1);
                } else

                if (obfuscated.equals("pl.asie.computronics.tile.TileSpeaker") && Config.computronicsPatching) {
                    // Inside TileSpeaker
                    InsnList toInject = new InsnList();

                    toInject.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "pl/asie/computronics/Computronics",
                        "speaker", "Lpl/asie/computronics/block/BlockSpeaker;"));
                    toInject.add(new FieldInsnNode(Opcodes.GETFIELD, "pl/asie/computronics/block/BlockSpeaker", "rotation",
                        "Lpl/asie/lib/block/BlockBase$Rotation;"));
                    toInject.add(new FieldInsnNode(Opcodes.GETFIELD, "pl/asie/lib/block/BlockBase$Rotation", "FACING",
                        "Lnet/minecraft/block/properties/PropertyDirection;"));
                    toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/sonicether/soundphysics/SoundPhysics", "computronicsOffset",
                        "(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/tileentity/TileEntity;Lnet/minecraft/block/properties/PropertyDirection;)Lnet/minecraft/util/math/Vec3d;", false));

                    // Target method: getSoundPos
                    bytes = patchMethodInClass(obfuscated, bytes, "getSoundPos", "()Lnet/minecraft/util/math/Vec3d;", Opcodes.ARETURN,
                        AbstractInsnNode.INSN, "", null, -1, toInject, true, 0, 0, false, 0, -1);
                } else

                if (obfuscated.equals("pl.asie.computronics.tile.TileSpeechBox$1") && Config.computronicsPatching) {
                    // Inside TileSpeechBox.internalSpeaker
                    InsnList toInject = new InsnList();

                    toInject.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    toInject.add(new FieldInsnNode(Opcodes.GETFIELD, "pl/asie/computronics/tile/TileSpeechBox$1", "this$0",
                        "Lpl/asie/computronics/tile/TileSpeechBox;"));
                    toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "pl/asie/computronics/Computronics",
                        "speechBox", "Lpl/asie/computronics/block/BlockSpeechBox;"));
                    toInject.add(new FieldInsnNode(Opcodes.GETFIELD, "pl/asie/computronics/block/BlockSpeechBox", "rotation",
                        "Lpl/asie/lib/block/BlockBase$Rotation;"));
                    toInject.add(new FieldInsnNode(Opcodes.GETFIELD, "pl/asie/lib/block/BlockBase$Rotation", "FACING",
                        "Lnet/minecraft/block/properties/PropertyDirection;"));
                    toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/sonicether/soundphysics/SoundPhysics", "computronicsOffset",
                        "(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/tileentity/TileEntity;Lnet/minecraft/block/properties/PropertyDirection;)Lnet/minecraft/util/math/Vec3d;", false));

                    // Target method: getSoundPos
                    bytes = patchMethodInClass(obfuscated, bytes, "getSoundPos", "()Lnet/minecraft/util/math/Vec3d;", Opcodes.ARETURN,
                        AbstractInsnNode.INSN, "", null, -1, toInject, true, 0, 0, false, 0, -1);
                } else

                if ((obfuscated.equals("cam72cam.immersiverailroading.sound.ClientSound") || obfuscated.equals("cam72cam.mod.sound.ClientSound")) && Config.irPatching) {
                    // Inside ClientSound
                    InsnList toInject = new InsnList();

                    final boolean newIR = obfuscated.equals("cam72cam.mod.sound.ClientSound");
                    final String classCS = obfuscated.replace(".","/");
                    final String playDesc = newIR ? "(Lcam72cam/mod/math/Vec3d;)V" : "(Lnet/minecraft/util/math/Vec3d;)V";
                    final String classRes = newIR ? "cam72cam/mod/resource/Identifier" : "nf";

                    toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "qg","i", "Lqg;")); // Ambient sound category
                    toInject.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    toInject.add(new FieldInsnNode(Opcodes.GETFIELD, classCS, "oggLocation",
                        "L"+classRes+";"));
                    toInject.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classRes, "toString", "()Ljava/lang/String;", false));
                    toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/sonicether/soundphysics/SoundPhysics",
                        "setLastSound", "(Lqg;Ljava/lang/String;)V", false));

                    // Target method: play
                    bytes = patchMethodInClass(obfuscated, bytes, "play", playDesc, Opcodes.INVOKEVIRTUAL,
                        AbstractInsnNode.METHOD_INSN, "update", null, -1, toInject, false, 0, 0, false, 0, -1);

                    toInject = new InsnList();

                    toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
                        "soundDistanceAllowance", "D"));
                    toInject.add(new InsnNode(Opcodes.DMUL));

                    // Target method: play
                    bytes = patchMethodInClass(obfuscated, bytes, "play", playDesc, Opcodes.DCMPG,
                        AbstractInsnNode.INSN, "", null, -1, toInject, true, 0, 0, false, 0, -1);

                    toInject = new InsnList();

                    // TODO: use applyGlobalVolumeMultiplier here
                    toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
                        "globalVolumeMultiplier0", "F"));
                    toInject.add(new InsnNode(Opcodes.FMUL));

                    // Target method: update
                    bytes = patchMethodInClass(obfuscated, bytes, "update", "()V", Opcodes.FMUL,
                        AbstractInsnNode.INSN, "", null, -1, toInject, true, 0, 0, false, 0, -1);

                    // Commented code to change the position of the sound source depending on the scale of the train
                    // Could be implemented but needs more work/proper positions for like the wheels and stuff
			/*toInject = new InsnList();

			toInject.add(new LdcInsnNode(1.75d));
			toInject.add(new VarInsnNode(Opcodes.ALOAD, 0));
			toInject.add(new FieldInsnNode(Opcodes.GETFIELD, "cam72cam/immersiverailroading/sound/ClientSound", "gauge",
					"Lcam72cam/immersiverailroading/library/Gauge;"));
			toInject.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "cam72cam/immersiverailroading/library/Gauge", "scale", "()D", false));
			toInject.add(new InsnNode(Opcodes.DMUL));
			toInject.add(new InsnNode(Opcodes.DADD));

			// Target method: update
			bytes = patchMethodInClass(obfuscated, bytes, "update", "()V", Opcodes.INVOKESPECIAL,
					AbstractInsnNode.METHOD_INSN, "<init>", "(ILjava/lang/String;FFF)V", -1, toInject, true, 0, 0, false, -5, -1);*/
                } else

                if (obfuscated.equals("org.orecruncher.dsurround.client.sound.SoundEffect") && shouldPatchDS(false)) {
                    // Inside SoundEffect
                    InsnList toInject = new InsnList();

                    toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "org/orecruncher/dsurround/client/sound/SoundInstance",
                        "noAttenuation", "()Lcgt$a;", false));

                    // Target method: createTrackingSound
                    bytes = patchMethodInClass(obfuscated, bytes, "createTrackingSound", "(Lnet/minecraft/entity/Entity;Z)Lorg/orecruncher/dsurround/client/sound/SoundInstance;", Opcodes.GETSTATIC,
                        AbstractInsnNode.FIELD_INSN, "", null, -1, toInject, true, 0, 0, true, 0, -1);
                } else

                if (obfuscated.equals("org.orecruncher.dsurround.client.sound.ConfigSoundInstance") && shouldPatchDS(true)) {
                    // Inside ConfigSoundInstance
                    InsnList toInject = new InsnList();

                    toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "cgt$a","a", "Lcgt$a;")); // ISound.AttenuationType.NONE

                    // Target method: constructor
                    bytes = patchMethodInClass(obfuscated, bytes, "<init>", "(Ljava/lang/String;F)V", Opcodes.INVOKESTATIC,
                        AbstractInsnNode.METHOD_INSN, "noAttenuation", null, -1, toInject, false, 0, 0, true, 0, -1);
                } else

                if (obfuscated.equals("com.mushroom.midnight.client.SoundReverbHandler") && Config.midnightPatching) {
                    // Inside SoundReverbHandler
                    InsnList toInject = new InsnList();

                    toInject.add(new InsnNode(Opcodes.RETURN));
                    toInject.add(new FrameNode(Opcodes.F_SAME,0,new Object[] {},0,new Object[] {}));

                    // Target method: onPlaySound
                    bytes = patchMethodInClass(obfuscated, bytes, "onPlaySound", "(I)V", Opcodes.GETSTATIC,
                        AbstractInsnNode.FIELD_INSN, "", null, -1, toInject, true, 0, 0, false, 0, 0);
                } else

                if (obfuscated.equals("ic2.core.audio.AudioManagerClient") && Config.ic2Patching) {
                    // Inside AudioManagerClient
                    InsnList toInject = new InsnList();

                    final boolean ic2Classic = isIC2Classic();
                    String posSpecClass = "ic2/core/audio/PositionSpec";
                    String playOnceDesc = "(Ljava/lang/Object;Lic2/core/audio/PositionSpec;Ljava/lang/String;ZF)Ljava/lang/String;";
                    int quickPlayRemoveCnt = 7;
                    if (ic2Classic) {
                        posSpecClass = "ic2/api/classic/audio/PositionSpec";
                        playOnceDesc = "(Ljava/lang/Object;Lic2/api/classic/audio/PositionSpec;Lnet/minecraft/util/ResourceLocation;ZF)V";
                        quickPlayRemoveCnt = 6;
                    }

                    toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
                        "attenuationModel", "I"));
                    toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
                        "globalRolloffFactor", "F"));
                    toInject.add(new VarInsnNode(Opcodes.ALOAD, 2));
                    toInject.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, posSpecClass, "ordinal", "()I", false));
                    toInject.add(new VarInsnNode(Opcodes.ALOAD, 3));
                    if (ic2Classic)//toString
                        toInject.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "nf", "func_110623_a", "()Ljava/lang/String;", false));
                    toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/sonicether/soundphysics/SoundPhysics",
                        "setLastSound", "(ILjava/lang/String;)V", false));

                    // Target method: playOnce
                    bytes = patchMethodInClass(obfuscated, bytes, "playOnce", playOnceDesc, Opcodes.INVOKEVIRTUAL,
                        AbstractInsnNode.METHOD_INSN, "quickPlay", null, -1, toInject, true, quickPlayRemoveCnt, 0, false, 0, -1);

                    toInject = new InsnList();

                    toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/sonicether/soundphysics/SoundPhysics",
                        "applyGlobalVolumeMultiplier", "(F)F", false));

                    // Target method: playOnce
                    bytes = patchMethodInClass(obfuscated, bytes, "playOnce", playOnceDesc, Opcodes.INVOKEVIRTUAL,
                        AbstractInsnNode.METHOD_INSN, "setVolume", null, -1, toInject, true, 0, 0, false, 0, -1);
                } else

                if (obfuscated.equals("ic2.core.audio.AudioSourceClient") && Config.ic2Patching) {
                    // Inside AudioSourceClient
                    InsnList toInject = new InsnList();

                    final boolean ic2Classic = isIC2Classic();
                    String posSpecClass = "ic2/core/audio/PositionSpec";
                    String audioPosClass = "ic2/core/audio/AudioPosition";
                    String posSpecField = "positionSpec";
                    int cmpgType = Opcodes.FCMPG;
                    if (ic2Classic) {
                        posSpecClass = "ic2/api/classic/audio/PositionSpec";
                        audioPosClass = "ic2/api/classic/audio/IAudioPosition";
                        posSpecField = "soundType";
                        cmpgType = Opcodes.DCMPG;
                    }

                    toInject.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    toInject.add(new FieldInsnNode(Opcodes.GETFIELD, "ic2/core/audio/AudioSourceClient", posSpecField,
                        "L"+posSpecClass+";"));
                    toInject.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, posSpecClass, "ordinal", "()I", false));
                    if (ic2Classic) {
                        toInject.add(new InsnNode(Opcodes.ACONST_NULL));
                    } else {
                        toInject.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        toInject.add(new FieldInsnNode(Opcodes.GETFIELD, "ic2/core/audio/AudioSourceClient", "initialSoundFile",
                            "Ljava/lang/String;"));
                    }
                    toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/sonicether/soundphysics/SoundPhysics",
                        "setLastSound", "(ILjava/lang/String;)V", false));

                    // Target method: play
                    bytes = patchMethodInClass(obfuscated, bytes, "play", "()V", Opcodes.INVOKEVIRTUAL,
                        AbstractInsnNode.METHOD_INSN, "play", null, -1, toInject, true, 0, 0, false, 0, -1);

                    if (ic2Classic) {
                        toInject = new InsnList();

				/*toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
						"globalVolumeMultiplier0", "F"));
				toInject.add(new InsnNode(Opcodes.FMUL));*/

                        bytes = patchMethodInClass(obfuscated, bytes, "updateVolume", "(Lnet/minecraft/entity/player/EntityPlayer;)V", Opcodes.INVOKEINTERFACE,
                            AbstractInsnNode.METHOD_INSN, "getPosition", null, -1, toInject, true, 14, 0, false, -17, 1);
                    } else {
                        toInject = new InsnList();

                        toInject.add(new InsnNode(Opcodes.FCONST_1));

                        // Target method: updateVolume
                        bytes = patchMethodInClass(obfuscated, bytes, "updateVolume", "(Lnet/minecraft/entity/player/EntityPlayer;)V", Opcodes.INVOKEVIRTUAL,
                            AbstractInsnNode.METHOD_INSN, "getMasterVolume", null, -1, toInject, false, 0, 0, true, -5, 0);

                        toInject = new InsnList();

				/*toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
						"globalVolumeMultiplier0", "F"));*/
                        toInject.add(new InsnNode(Opcodes.FCONST_1));

                        // Target method: updateVolume
                        bytes = patchMethodInClass(obfuscated, bytes, "updateVolume", "(Lnet/minecraft/entity/player/EntityPlayer;)V", Opcodes.INVOKEVIRTUAL,
                            AbstractInsnNode.METHOD_INSN, "getMasterVolume", null, -1, toInject, false, 1, 0, true, 0, 1);
                    }

                    if (ic2Classic) {
                        toInject = new InsnList();

                        toInject.add(new VarInsnNode(Opcodes.ALOAD, 1));
                        toInject.add(new InsnNode(Opcodes.ACONST_NULL));
                        toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/sonicether/soundphysics/SoundPhysics",
                            "calculateEntitySoundOffsetVec", "(Lbhe;Lvg;Lqe;)Lbhe;", false));
                        toInject.add(new VarInsnNode(Opcodes.DLOAD, 3));
                        toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
                            "soundDistanceAllowance", "D"));
                        toInject.add(new InsnNode(Opcodes.DMUL));
                        toInject.add(new VarInsnNode(Opcodes.DSTORE, 3));

                        // Target method: updateVolume
                        bytes = patchMethodInClass(obfuscated, bytes, "updateVolume", "(Lnet/minecraft/entity/player/EntityPlayer;)V", Opcodes.INVOKEVIRTUAL,
                            AbstractInsnNode.METHOD_INSN, "func_174791_d", null, -1, toInject, false, 0, 0, false, 0, -1);
                    } else {
                        toInject = new InsnList();

                        toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
                            "soundDistanceAllowance", "D"));
                        toInject.add(new InsnNode(Opcodes.D2F));
                        toInject.add(new InsnNode(Opcodes.FMUL));

                        // Target method: updateVolume
                        bytes = patchMethodInClass(obfuscated, bytes, "updateVolume", "(Lnet/minecraft/entity/player/EntityPlayer;)V", Opcodes.INVOKESTATIC,
                            AbstractInsnNode.METHOD_INSN, "max", null, -1, toInject, false, 0, 0, false, 1, -1);

                        toInject = new InsnList();

                        toInject.add(new VarInsnNode(Opcodes.ALOAD, 1));
                        toInject.add(new InsnNode(Opcodes.ACONST_NULL));
                        toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/sonicether/soundphysics/SoundPhysics",
                            "calculateEntitySoundOffset", "(Lvg;Lqe;)D", false));
                        toInject.add(new InsnNode(Opcodes.DADD));

                        // Target method: updateVolume
                        bytes = patchMethodInClass(obfuscated, bytes, "updateVolume", "(Lnet/minecraft/entity/player/EntityPlayer;)V", Opcodes.INVOKEVIRTUAL,
                            AbstractInsnNode.METHOD_INSN, "getWorld", null, -1, toInject, true, 0, 0, false, -15, -1); // -11 without labels
                    }

                    toInject = new InsnList();

                    if (ic2Classic) {
                        toInject.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        toInject.add(new FieldInsnNode(Opcodes.GETFIELD, "ic2/core/audio/AudioSourceClient", "position",
                            "Lic2/api/classic/audio/IAudioPosition;"));
                        toInject.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "ic2/api/classic/audio/IAudioPosition",
                            "getPosition", "()Lnet/minecraft/util/math/Vec3d;"));
                        toInject.add(new VarInsnNode(Opcodes.ALOAD, 7));
                        toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/sonicether/soundphysics/SoundPhysics",
                            "ic2DistanceCheckHook", "(DDLbhe;Lbhe;)I", false));
                    } else {
                        toInject.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        toInject.add(new FieldInsnNode(Opcodes.GETFIELD, "ic2/core/audio/AudioSourceClient", "position",
                            "Lic2/core/audio/AudioPosition;"));
                        toInject.add(new FieldInsnNode(Opcodes.GETFIELD, "ic2/core/audio/AudioPosition", "x", "F"));
                        toInject.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        toInject.add(new FieldInsnNode(Opcodes.GETFIELD, "ic2/core/audio/AudioSourceClient", "position",
                            "Lic2/core/audio/AudioPosition;"));
                        toInject.add(new FieldInsnNode(Opcodes.GETFIELD, "ic2/core/audio/AudioPosition", "y", "F"));
                        toInject.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        toInject.add(new FieldInsnNode(Opcodes.GETFIELD, "ic2/core/audio/AudioSourceClient", "position",
                            "Lic2/core/audio/AudioPosition;"));
                        toInject.add(new FieldInsnNode(Opcodes.GETFIELD, "ic2/core/audio/AudioPosition", "z", "F"));
                        toInject.add(new VarInsnNode(Opcodes.FLOAD, 6));
                        toInject.add(new VarInsnNode(Opcodes.FLOAD, 7));
                        toInject.add(new VarInsnNode(Opcodes.FLOAD, 8));
                        toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/sonicether/soundphysics/SoundPhysics",
                            "ic2DistanceCheckHook", "(FFFFFFFF)I", false));
                    }

                    // Target method: updateVolume
                    bytes = patchMethodInClass(obfuscated, bytes, "updateVolume", "(Lnet/minecraft/entity/player/EntityPlayer;)V", cmpgType,
                        AbstractInsnNode.INSN, null, null, -1, toInject, false, 0, 0, true, 0, -1);
                } else

                if (obfuscated.equals("net.gliby.voicechat.client.sound.ClientStreamManager") && Config.glibyVCSrcPatching) {

                    InsnList toInject = new InsnList();

                    toInject.add(new VarInsnNode(Opcodes.ALOAD, 4));
                    toInject.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/gliby/voicechat/common/PlayerProxy", "getPlayer", "()Lnet/minecraft/entity/Entity;", false));
                    toInject.add(new InsnNode(Opcodes.ACONST_NULL));
                    toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/sonicether/soundphysics/SoundPhysics",
                        "calculateEntitySoundOffset", "(Lvg;Lqe;)D", false));
                    toInject.add(new InsnNode(Opcodes.D2F));
                    toInject.add(new InsnNode(Opcodes.FADD));

                    // Target method: createStream
                    bytes = patchMethodInClass(obfuscated, bytes, "createStream", "(Lnet/gliby/voicechat/client/sound/Datalet;)V", Opcodes.INVOKEVIRTUAL,
                        AbstractInsnNode.METHOD_INSN, "rawDataStream", null, -1, toInject, true, 0, 0, false, -8, 0);

                    toInject = new InsnList();

                    toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
                        "attenuationModel", "I"));
                    toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
                        "globalRolloffFactor", "F"));

                    // Target method: createStream
                    bytes = patchMethodInClass(obfuscated, bytes, "createStream", "(Lnet/gliby/voicechat/client/sound/Datalet;)V", Opcodes.INVOKEVIRTUAL,
                        AbstractInsnNode.METHOD_INSN, "rawDataStream", null, -1, toInject, true, 6, 0, false, 0, 0);

                    toInject = new InsnList();

                    toInject.add(new InsnNode(Opcodes.DUP));
                    toInject.add(new VarInsnNode(Opcodes.ASTORE, 6));
                    toInject.add(new InsnNode(Opcodes.ACONST_NULL));
                    toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/sonicether/soundphysics/SoundPhysics",
                        "calculateEntitySoundOffset", "(Lvg;Lqe;)D", false));
                    toInject.add(new VarInsnNode(Opcodes.ALOAD, 6));
                    toInject.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/client/entity/EntityPlayerSP", "field_70163_u", "D"));
                    toInject.add(new InsnNode(Opcodes.DADD));

                    // Target method: createStream
                    bytes = patchMethodInClass(obfuscated, bytes, "createStream", "(Lnet/gliby/voicechat/client/sound/Datalet;)V", Opcodes.INVOKEVIRTUAL,
                        AbstractInsnNode.METHOD_INSN, "rawDataStream", null, -1, toInject, true, 0, 0, true, -13, 1);

                    toInject = new InsnList();

                    toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
                        "attenuationModel", "I"));
                    toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
                        "globalRolloffFactor", "F"));

                    // Target method: createStream
                    bytes = patchMethodInClass(obfuscated, bytes, "createStream", "(Lnet/gliby/voicechat/client/sound/Datalet;)V", Opcodes.INVOKEVIRTUAL,
                        AbstractInsnNode.METHOD_INSN, "rawDataStream", null, -1, toInject, true, 6, 0, false, 0, 1);

                    toInject = new InsnList();

                    toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
                        "globalVolumeMultiplier0", "F"));
                    toInject.add(new InsnNode(Opcodes.FMUL));

                    // Target method: createStream
                    bytes = patchMethodInClass(obfuscated, bytes, "createStream", "(Lnet/gliby/voicechat/client/sound/Datalet;)V", Opcodes.INVOKEVIRTUAL,
                        AbstractInsnNode.METHOD_INSN, "setVolume", null, -1, toInject, true, 0, 0, false, 0, 0);

                    toInject = new InsnList();

                    toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
                        "globalVolumeMultiplier0", "F"));
                    toInject.add(new InsnNode(Opcodes.FMUL));

                    // Target method: createStream
                    bytes = patchMethodInClass(obfuscated, bytes, "createStream", "(Lnet/gliby/voicechat/client/sound/Datalet;)V", Opcodes.INVOKEVIRTUAL,
                        AbstractInsnNode.METHOD_INSN, "setVolume", null, -1, toInject, true, 0, 0, false, 0, 1);

                    toInject = new InsnList();

                    toInject.add(new VarInsnNode(Opcodes.ALOAD, 4));
                    toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/sonicether/soundphysics/SoundPhysics",
                        "onPlaySound", "(Ljava/lang/String;)V", false));

                    // Target method: giveStream
                    bytes = patchMethodInClass(obfuscated, bytes, "giveStream", "(Lnet/gliby/voicechat/client/sound/Datalet;)V", Opcodes.INVOKEVIRTUAL,
                        AbstractInsnNode.METHOD_INSN, "feedRawAudioData", null, -1, toInject, false, 0, 0, false, 0, -1);

                } else

                if (obfuscated.equals("net.gliby.voicechat.client.sound.thread.ThreadUpdateStream") && Config.glibyVCSrcPatching) {

                    InsnList toInject = new InsnList();

                    toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
                        "globalVolumeMultiplier0", "F"));
                    toInject.add(new InsnNode(Opcodes.FMUL));

                    // Target method: run
                    bytes = patchMethodInClass(obfuscated, bytes, "run", "()V", Opcodes.INVOKEVIRTUAL,
                        AbstractInsnNode.METHOD_INSN, "setVolume", null, -1, toInject, true, 0, 0, false, 0, 0);

                    toInject = new InsnList();

                    toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
                        "globalVolumeMultiplier0", "F"));
                    toInject.add(new InsnNode(Opcodes.FMUL));

                    // Target method: run
                    bytes = patchMethodInClass(obfuscated, bytes, "run", "()V", Opcodes.INVOKEVIRTUAL,
                        AbstractInsnNode.METHOD_INSN, "setVolume", null, -1, toInject, true, 0, 0, false, 0, 1);

                    toInject = new InsnList();

                    toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
                        "attenuationModel", "I"));

                    // Target method: run
                    bytes = patchMethodInClass(obfuscated, bytes, "run", "()V", Opcodes.INVOKEVIRTUAL,
                        AbstractInsnNode.METHOD_INSN, "setAttenuation", null, -1, toInject, true, 1, 0, false, 0, -1);

                    toInject = new InsnList();

                    toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
                        "globalRolloffFactor", "F"));

                    // Target method: run
                    bytes = patchMethodInClass(obfuscated, bytes, "run", "()V", Opcodes.INVOKEVIRTUAL,
                        AbstractInsnNode.METHOD_INSN, "setDistOrRoll", null, -1, toInject, true, 5, 0, false, 0, -1);

                    toInject = new InsnList();

                    toInject.add(new VarInsnNode(Opcodes.ALOAD, 3));
                    toInject.add(new FieldInsnNode(Opcodes.GETFIELD, "net/gliby/voicechat/client/sound/ClientStream", "player", "Lnet/gliby/voicechat/common/PlayerProxy;"));
                    toInject.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/gliby/voicechat/common/PlayerProxy", "getPlayer", "()Lnet/minecraft/entity/Entity;", false));
                    toInject.add(new InsnNode(Opcodes.ACONST_NULL));
                    toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/sonicether/soundphysics/SoundPhysics",
                        "calculateEntitySoundOffset", "(Lvg;Lqe;)D", false));
                    toInject.add(new InsnNode(Opcodes.D2F));
                    toInject.add(new InsnNode(Opcodes.FADD));

                    // Target method: createStream
                    bytes = patchMethodInClass(obfuscated, bytes, "run", "()V", Opcodes.INVOKEVIRTUAL,
                        AbstractInsnNode.METHOD_INSN, "setPosition", null, -1, toInject, true, 0, 0, false, -2, 0);

                    toInject = new InsnList();

                    toInject.add(new InsnNode(Opcodes.DUP));
                    toInject.add(new VarInsnNode(Opcodes.ASTORE, 7));
                    toInject.add(new InsnNode(Opcodes.ACONST_NULL));
                    toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/sonicether/soundphysics/SoundPhysics",
                        "calculateEntitySoundOffset", "(Lvg;Lqe;)D", false));
                    toInject.add(new VarInsnNode(Opcodes.ALOAD, 7));
                    toInject.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/client/entity/EntityPlayerSP", "field_70163_u", "D"));
                    toInject.add(new InsnNode(Opcodes.DADD));

                    // Target method: createStream
                    bytes = patchMethodInClass(obfuscated, bytes, "run", "()V", Opcodes.INVOKEVIRTUAL,
                        AbstractInsnNode.METHOD_INSN, "setPosition", null, -1, toInject, true, 0, 0, true, -7, 1);

                    toInject = new InsnList();

                    toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
                        "globalVolumeMultiplier0", "F"));
                    toInject.add(new InsnNode(Opcodes.FMUL));

                    // Target method: run
                    bytes = patchMethodInClass(obfuscated, bytes, "run", "()V", Opcodes.INVOKEVIRTUAL,
                        AbstractInsnNode.METHOD_INSN, "setVolume", null, -1, toInject, true, 0, 0, false, 0, 2);

                } else

                if (obfuscated.equals("net.gliby.voicechat.common.networking.ServerStreamManager") && Config.glibyVCSrcPatching) {

                    InsnList toInject = new InsnList();

                    toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
                        "soundDistanceAllowance", "D"));
                    toInject.add(new InsnNode(Opcodes.DMUL));

                    // Target method: feedWithinEntityWithRadius
                    bytes = patchMethodInClass(obfuscated, bytes, "feedWithinEntityWithRadius",
                        "(Lnet/gliby/voicechat/common/networking/ServerStream;Lnet/gliby/voicechat/common/networking/ServerDatalet;)V",
                        Opcodes.DCMPG, AbstractInsnNode.INSN, null, null, -1, toInject, true, 0, 0, false, 0, 0);

                    toInject = new InsnList();

                    toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
                        "soundDistanceAllowance", "D"));
                    toInject.add(new InsnNode(Opcodes.DMUL));

                    // Target method: feedWithinEntityWithRadius
                    bytes = patchMethodInClass(obfuscated, bytes, "feedWithinEntityWithRadius",
                        "(Lnet/gliby/voicechat/common/networking/ServerStream;Lnet/gliby/voicechat/common/networking/ServerDatalet;)V",
                        Opcodes.DCMPG, AbstractInsnNode.INSN, null, null, -1, toInject, true, 0, 0, false, 0, 1);

                }

        //log("Finished processing class: '"+obfuscated+"' ('"+deobfuscated+"')");

        return bytes;
    }

    private static final Printer printer = new Textifier();
    private static final TraceMethodVisitor mp = new TraceMethodVisitor(printer);

    public static String insnToString(AbstractInsnNode insn) {
        insn.accept(mp);
        StringWriter sw = new StringWriter();
        printer.print(new PrintWriter(sw));
        printer.getText().clear();
        return sw.toString();
    }

    private byte[] patchMethodInClass(String className, final byte[] bytes, final String targetMethod,
                                      final String targetMethodSignature, final int targetNodeOpcode, final int targetNodeType,
                                      final String targetInvocationMethodName, final String targetInvocationMethodSignature, final int targetVarNodeIndex,
                                      final InsnList instructionsToInject, final boolean insertBefore, final int nodesToDeleteBefore,
                                      final int nodesToDeleteAfter, final boolean deleteTargetNode, final int targetNodeOffset, final int targetNodeNumber) {
        log("Patching class : "+className);

        final ClassNode classNode = new ClassNode();
        final ClassReader classReader = new ClassReader(bytes);
        classReader.accept(classNode, 0);

        for (MethodNode m : classNode.methods) {
            //log("@" + m.name + " " + m.desc);

            if (m.name.equals(targetMethod) && m.desc.equals(targetMethodSignature)) {
                log("Inside target method: " + targetMethod);

                AbstractInsnNode targetNode = null;
                int targetNodeNb = 0;

                for (AbstractInsnNode currentNode : m.instructions) {
                    //log(insnToString(currentNode).replace("\n", ""));
                    if (currentNode.getOpcode() == targetNodeOpcode) {

                        if (targetNodeType == AbstractInsnNode.METHOD_INSN) {
                            if (currentNode.getType() == AbstractInsnNode.METHOD_INSN) {
                                final MethodInsnNode method = (MethodInsnNode) currentNode;
                                if (method.name.equals(targetInvocationMethodName)) {
                                    if (method.desc.equals(targetInvocationMethodSignature)
                                        || targetInvocationMethodSignature == null) {
                                        log("Found target method invocation for injection: " + targetInvocationMethodName);
                                        targetNode = currentNode;
                                        if (targetNodeNumber >= 0 && targetNodeNb == targetNodeNumber) break;
                                        targetNodeNb++;
                                    }

                                }
                            }
                        } else if (targetNodeType == AbstractInsnNode.VAR_INSN) {
                            if (currentNode.getType() == AbstractInsnNode.VAR_INSN) {
                                final VarInsnNode varnode = (VarInsnNode) currentNode;
                                if (targetVarNodeIndex < 0 || varnode.var == targetVarNodeIndex) {
                                    log("Found target var node for injection: " + targetVarNodeIndex);
                                    targetNode = currentNode;
                                    if (targetNodeNumber >= 0 && targetNodeNb == targetNodeNumber) break;
                                    targetNodeNb++;
                                }
                            }
                        } else {
                            if (currentNode.getType() == targetNodeType) {
                                log("Found target node for injection: " + targetNodeType);
                                targetNode = currentNode;
                                if (targetNodeNumber >= 0 && targetNodeNb == targetNodeNumber) break;
                                targetNodeNb++;
                            }
                        }

                    }
                }

                if (targetNode == null) {
                    logError("Target node not found! " + className);
                    break;
                }

                // Offset the target node by the supplied offset value
                if (targetNodeOffset > 0) {
                    for (int i = 0; i < targetNodeOffset; i++) {
                        targetNode = targetNode.getNext();
                    }
                } else if (targetNodeOffset < 0) {
                    for (int i = 0; i < -targetNodeOffset; i++) {
                        targetNode = targetNode.getPrevious();
                    }
                }

                // If we've found the target, inject the instructions!
                for (int i = 0; i < nodesToDeleteBefore; i++) {
                    final AbstractInsnNode previousNode = targetNode.getPrevious();
                    //log("Removing Node " + insnToString(previousNode).replace("\n", ""));
                    log("Removing Node " + previousNode.getOpcode());
                    m.instructions.remove(previousNode);
                }

                for (int i = 0; i < nodesToDeleteAfter; i++) {
                    final AbstractInsnNode nextNode = targetNode.getNext();
                    //log("Removing Node " + insnToString(nextNode).replace("\n", ""));
                    log("Removing Node " + nextNode.getOpcode());
                    m.instructions.remove(nextNode);
                }

                if (insertBefore) {
                    m.instructions.insertBefore(targetNode, instructionsToInject);
                } else {
                    m.instructions.insert(targetNode, instructionsToInject);
                }

                if (deleteTargetNode) {
                    m.instructions.remove(targetNode);
                }

                break;
            }
        }
        log("Class finished : "+className);

        final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    public static void log(final String message) {
        if (Config.injectorLogging) logger.info(message);
    }

    public static void logError(final String errorMessage) {
        logger.error(errorMessage);
    }
}
