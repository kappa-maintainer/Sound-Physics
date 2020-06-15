package com.sonicether.soundphysics;

import java.lang.reflect.Field;

import java.util.Vector;
import java.util.Set;
import java.util.HashSet;
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
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.FrameNode;

import org.objectweb.asm.util.TraceMethodVisitor;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import net.minecraftforge.fml.loading.FMLLoader;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TransformerVoteResult;

public class CoreModInjector implements ITransformer<ClassNode> {

	public static final Logger logger = LogManager.getLogger(SoundPhysics.modid+"_injector");

	@Override
	public TransformerVoteResult castVote(ITransformerVotingContext context) {
		return TransformerVoteResult.YES;
	}

	@Override
	public Set<ITransformer.Target> targets() {
		Set<ITransformer.Target> targets = new HashSet<ITransformer.Target>();
		targets.add(ITransformer.Target.targetClass("net/minecraft/client/audio/SoundEngine"));
		targets.add(ITransformer.Target.targetClass("net/minecraft/client/audio/AudioStreamManager"));
		targets.add(ITransformer.Target.targetClass("net/minecraft/client/audio/SoundSource"));
		targets.add(ITransformer.Target.targetClass("net/minecraft/client/audio/Sound"));
		targets.add(ITransformer.Target.targetClass("net/minecraft/client/audio/SoundSystem"));
		targets.add(ITransformer.Target.targetClass("net/minecraft/server/management/PlayerList"));
		targets.add(ITransformer.Target.targetClass("net/minecraft/entity/Entity"));
		return targets;
	}

	@Override
	public ClassNode transform(ClassNode classNode, ITransformerVotingContext context) {
		String className = classNode.name;
		log("Wants to transform: '"+className+"'");

		if (className.equals("net/minecraft/client/audio/SoundEngine")) {
			InsnList toInject = new InsnList();
			toInject.add(new VarInsnNode(Opcodes.ALOAD, 0));
			toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/sonicether/soundphysics/SoundPhysics", "init",
					"(Lnet/minecraft/client/audio/SoundEngine;)V", false));

			// Target method: load
			classNode = patchMethodInClass(classNode, "func_148608_i", "()V", Opcodes.INVOKEINTERFACE,
					AbstractInsnNode.METHOD_INSN, "info", null, -1, toInject, false, 0, 0, false, 0, -1);

			toInject = new InsnList();
			toInject.add(new VarInsnNode(Opcodes.ALOAD, 1));
			toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/sonicether/soundphysics/SoundPhysics",
					"applyGlobalVolumeMultiplier", "(FLnet/minecraft/client/audio/ISound;)F", false));

			// Target method: getClampedVolume
			classNode = patchMethodInClass(classNode, "func_188770_e", "(Lnet/minecraft/client/audio/ISound;)F", Opcodes.FRETURN,
					AbstractInsnNode.INSN, null, null, -1, toInject, true, 0, 0, false, 0, -1);

			toInject = new InsnList();
			toInject.add(new VarInsnNode(Opcodes.ALOAD, 2));
			toInject.add(new VarInsnNode(Opcodes.ALOAD, 3));
			toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/sonicether/soundphysics/SoundPhysics",
					"onPlaySound", "(Lnet/minecraft/client/audio/ISound;Lnet/minecraft/client/audio/SoundSource;)V", false));

			// Target method: Inside lambda of play
			classNode = patchMethodInClass(classNode, "lambda$null$5",
				"(Lnet/minecraft/client/audio/AudioStreamBuffer;Lnet/minecraft/client/audio/ISound;Lnet/minecraft/client/audio/SoundSource;)V",
				Opcodes.INVOKEVIRTUAL, AbstractInsnNode.METHOD_INSN, "func_216438_c", null, -1, toInject, true, 0, 0, false, 0, -1);
		} else

		if (className.equals("net/minecraft/client/audio/SoundSystem")) {
			InsnList toInject = new InsnList();
			toInject.add(new VarInsnNode(Opcodes.ALOAD, 1));
			toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/sonicether/soundphysics/SoundPhysics", "createALContext",
					"(JLorg/lwjgl/openal/ALCCapabilities;)J", false));

			// Target method: init
			classNode = patchMethodInClass(classNode, "func_216404_a", "()V", Opcodes.INVOKESTATIC,
					AbstractInsnNode.METHOD_INSN, "alcCreateContext", null, -1, toInject, true, 2, 0, true, 0, -1);
		} else

		// Convert stero sounds to mono
		if (className.equals("net/minecraft/client/audio/AudioStreamManager")) {
			/*
			// Streaming sources, not needed (I think at least)
			InsnList toInject = new InsnList();
			toInject.add(new VarInsnNode(Opcodes.ALOAD, 1));
			toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/sonicether/soundphysics/SoundPhysics", "streamLoad",
					"(Lnet/minecraft/util/ResourceLocation;)V", false));

			// Target method: Inside lambda of getStream
			classNode = patchMethodInClass(classNode, "func_217915_c", "(Lnet/minecraft/util/ResourceLocation;)Lnet/minecraft/client/audio/IAudioStream;", Opcodes.INVOKESPECIAL,
					AbstractInsnNode.METHOD_INSN, "<init>", "(Ljava/io/InputStream;)V", -1, toInject, false, 0, 0, false, 0, -1);
			*/

			InsnList toInject = new InsnList();
			toInject.add(new VarInsnNode(Opcodes.ALOAD, 1));
			toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/sonicether/soundphysics/SoundPhysics", "onLoadSound",
					"(Lnet/minecraft/client/audio/AudioStreamBuffer;Lnet/minecraft/util/ResourceLocation;)Lnet/minecraft/client/audio/AudioStreamBuffer;", false));

			// Target method: Inside lambda of getCompleteBuffer
			classNode = patchMethodInClass(classNode, "func_217914_e", "(Lnet/minecraft/util/ResourceLocation;)Lnet/minecraft/client/audio/AudioStreamBuffer;", Opcodes.INVOKESPECIAL,
					AbstractInsnNode.METHOD_INSN, "<init>", "(Ljava/nio/ByteBuffer;Ljavax/sound/sampled/AudioFormat;)V", -1, toInject, false, 0, 0, false, 0, -1);
		} else

		if (className.equals("net/minecraft/server/management/PlayerList")) {
			InsnList toInject = new InsnList();

			// Multiply sound distance volume play decision by
			// SoundPhysics.soundDistanceAllowance
			toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
					"soundDistanceAllowance", "D"));
			toInject.add(new InsnNode(Opcodes.DMUL));

			// Target method: sendToAllNearExcept
			classNode = patchMethodInClass(classNode, "func_148543_a",
				"(Lnet/minecraft/entity/player/PlayerEntity;DDDDLnet/minecraft/world/dimension/DimensionType;Lnet/minecraft/network/IPacket;)V",
				Opcodes.DCMPG, AbstractInsnNode.INSN, null, null, -1, toInject, true, 0, 0, false, 0, -1);
		} else

		if (className.equals("net/minecraft/client/audio/Sound")) {
			InsnList toInject = new InsnList();

			// Multiply sound distance volume play decision by
			// SoundPhysics.soundDistanceAllowance
			toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
					"soundDistanceAllowance", "D"));
			toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
					"soundDistanceAllowance", "D"));
			toInject.add(new InsnNode(Opcodes.DMUL));
			toInject.add(new InsnNode(Opcodes.D2I));
			toInject.add(new InsnNode(Opcodes.IMUL));

			// Target method: getAttenuationDistance
			classNode = patchMethodInClass(classNode, "func_206255_j", "()I",
				Opcodes.GETFIELD, AbstractInsnNode.FIELD_INSN, null, null, -1, toInject, false, 0, 0, false, 0, -1);
		} else 

		if (className.equals("net/minecraft/entity/Entity")) {
			InsnList toInject = new InsnList();

			// Offset entity sound by their eye height
			toInject.add(new VarInsnNode(Opcodes.ALOAD, 0));
			toInject.add(new VarInsnNode(Opcodes.ALOAD, 1));
			toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/sonicether/soundphysics/SoundPhysics",
					"calculateEntitySoundOffset", "(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/SoundEvent;)D", false));
			toInject.add(new InsnNode(Opcodes.DADD));

			// Target method: playSound
			// Inside target method, target node: Entity/getPosY
			classNode = patchMethodInClass(classNode, "func_184185_a", "(Lnet/minecraft/util/SoundEvent;FF)V", Opcodes.INVOKEVIRTUAL,
					AbstractInsnNode.METHOD_INSN, "func_226278_cu_", null, -1, toInject, false, 0, 0, false, 0, -1);
		} else

		if (className.equals("net/minecraft/client/audio/SoundSource")) {
			InsnList toInject = new InsnList();

			toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
					"attenuationModel", "I"));

			// Target method: linearAttenuation
			classNode = patchMethodInClass(classNode, "func_216423_c", "(F)V", Opcodes.INVOKESTATIC,
					AbstractInsnNode.METHOD_INSN, "alSourcei", null, -1, toInject, true, 1, 0, false, 0, -1);

			toInject = new InsnList();

			toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
					"globalRolloffFactor", "F"));

			// Target method: linearAttenuation
			classNode = patchMethodInClass(classNode, "func_216423_c", "(F)V", Opcodes.INVOKESTATIC,
					AbstractInsnNode.METHOD_INSN, "alSourcef", null, -1, toInject, true, 1, 0, false, 0, 1);

			toInject = new InsnList();

			// TODO: use applyGlobalVolumeMultiplier here
			toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/sonicether/soundphysics/SoundPhysics",
					"referenceDistance", "F"));

			// Target method: linearAttenuation
			classNode = patchMethodInClass(classNode, "func_216423_c", "(F)V", Opcodes.INVOKESTATIC,
					AbstractInsnNode.METHOD_INSN, "alSourcef", null, -1, toInject, true, 1, 0, false, 0, 2);
		}

		return classNode;
	}

	/*private static Printer printer = new Textifier();
	private static TraceMethodVisitor mp = new TraceMethodVisitor(printer);

	public static String insnToString(AbstractInsnNode insn) {
		insn.accept(mp);
		StringWriter sw = new StringWriter();
		printer.print(new PrintWriter(sw));
		printer.getText().clear();
		return sw.toString();
	}*/

	private ClassNode patchMethodInClass(ClassNode classNode, final String targetMethod,
			final String targetMethodSignature, final int targetNodeOpcode, final int targetNodeType,
			final String targetInvocationMethodName, final String targetInvocationMethodSignature, final int targetVarNodeIndex,
			final InsnList instructionsToInject, final boolean insertBefore, final int nodesToDeleteBefore,
			final int nodesToDeleteAfter, final boolean deleteTargetNode, final int targetNodeOffset, final int targetNodeNumber) {
		String className = classNode.name;
		log("Patching class : "+className);	

		final Iterator<MethodNode> methodIterator = classNode.methods.iterator();
		
		while (methodIterator.hasNext()) {
			final MethodNode m = methodIterator.next();
			//log("@" + m.name + " " + m.desc);

			if (m.name.equals(targetMethod) && m.desc.equals(targetMethodSignature)) {
				log("Inside target method: " + targetMethod);
				
				AbstractInsnNode targetNode = null;
				int targetNodeNb = 0;

				final ListIterator<AbstractInsnNode> nodeIterator = m.instructions.iterator();
				while (nodeIterator.hasNext()) {
					AbstractInsnNode currentNode = nodeIterator.next();
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

		return classNode;
	}

	public static void log(final String message) {
		logger.debug(message);
	}

	public static void logError(final String errorMessage) {
		logger.error(errorMessage);
	}
}
