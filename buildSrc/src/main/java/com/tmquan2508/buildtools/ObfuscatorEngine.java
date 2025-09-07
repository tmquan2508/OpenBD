package com.tmquan2508.buildtools;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.ListIterator;
import java.util.logging.Logger;

public final class ObfuscatorEngine {

    private static final Logger LOGGER = Logger.getLogger(ObfuscatorEngine.class.getName());

    public static byte[] transform(byte[] originalBytecode, String className) {
        try {
            ClassReader classReader = new ClassReader(originalBytecode);
            ClassNode classNode = new ClassNode();
            classReader.accept(classNode, ClassReader.EXPAND_FRAMES);

            if ((classNode.access & Opcodes.ACC_INTERFACE) != 0) {
                return originalBytecode;
            }

            applyControlFlowObfuscation(classNode);
            applyDecompilerCrash(classNode);
            applyMetadataRemoval(classNode);

            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            classNode.accept(classWriter);

            return classWriter.toByteArray();
        } catch (Exception e) {
            LOGGER.severe(String.format("Layered transformation for '%s' failed: %s", className, e.toString()));
            e.printStackTrace();
            return originalBytecode;
        }
    }

    private static void applyControlFlowObfuscation(ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0
                    || method.instructions.size() < 5
                    || method.name.startsWith("<")) {
                continue;
            }

            AbstractInsnNode insertionPoint = method.instructions.getFirst();
            if (insertionPoint == null) continue;

            InsnList newInstructions = new InsnList();
            LabelNode opaqueLabel = new LabelNode();
            LabelNode skipLabel = new LabelNode();

            newInstructions.add(new InsnNode(Opcodes.ICONST_1));
            newInstructions.add(new JumpInsnNode(Opcodes.IFEQ, opaqueLabel));
            newInstructions.add(new JumpInsnNode(Opcodes.GOTO, skipLabel));
            newInstructions.add(opaqueLabel);
            newInstructions.add(new TypeInsnNode(Opcodes.NEW, "java/lang/RuntimeException"));
            newInstructions.add(new InsnNode(Opcodes.DUP));
            newInstructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "()V", false));
            newInstructions.add(new InsnNode(Opcodes.ATHROW));
            newInstructions.add(skipLabel);

            method.instructions.insert(insertionPoint, newInstructions);
        }
    }

    private static void applyDecompilerCrash(ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0
                || method.instructions.size() < 2
                || method.name.startsWith("<")) {
                continue;
            }

            AbstractInsnNode insertionPoint = null;
            ListIterator<AbstractInsnNode> iterator = method.instructions.iterator(method.instructions.size());
            while (iterator.hasPrevious()) {
                AbstractInsnNode instruction = iterator.previous();
                int opcode = instruction.getOpcode();
                if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                    insertionPoint = instruction;
                    break;
                }
            }
            if (insertionPoint == null) continue;

            LabelNode start = new LabelNode();
            LabelNode end = new LabelNode();
            LabelNode handler = new LabelNode();
            LabelNode jumpOverHandler = new LabelNode();

            method.instructions.insert(start);
            method.instructions.add(end);

            InsnList handlerCode = new InsnList();
            handlerCode.add(new JumpInsnNode(Opcodes.GOTO, jumpOverHandler));
            handlerCode.add(handler);
            handlerCode.add(new TypeInsnNode(Opcodes.NEW, "java/lang/Error"));
            handlerCode.add(new InsnNode(Opcodes.DUP));
            handlerCode.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Error", "<init>", "()V", false));
            handlerCode.add(new InsnNode(Opcodes.ATHROW));
            handlerCode.add(jumpOverHandler);

            method.instructions.insertBefore(insertionPoint, handlerCode);

            if (method.tryCatchBlocks == null) {
                method.tryCatchBlocks = new ArrayList<>();
            }
            method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler, "java/lang/Throwable"));
        }
    }

    private static void applyMetadataRemoval(ClassNode classNode) {
        classNode.sourceFile = null;
        classNode.sourceDebug = null;
        for (MethodNode method : classNode.methods) {
            if (method.localVariables != null) {
                method.localVariables.clear();
            }
            ListIterator<AbstractInsnNode> iterator = method.instructions.iterator();
            while (iterator.hasNext()) {
                if (iterator.next() instanceof LineNumberNode) {
                    iterator.remove();
                }
            }
        }
    }
}