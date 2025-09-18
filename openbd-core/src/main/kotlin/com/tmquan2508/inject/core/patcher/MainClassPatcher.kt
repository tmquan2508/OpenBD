package com.tmquan2508.inject.core.patcher

import org.objectweb.asm.*

private class SafeClassWriter(classReader: ClassReader, flags: Int) : ClassWriter(classReader, flags) {
    override fun getCommonSuperClass(type1: String, type2: String): String {
        return "java/lang/Object"
    }
}

class MainClassPatcher {
    fun patch(
        originalMainClassBytes: ByteArray,
        finalMainPayloadCallName: String
    ): ByteArray {
        val finalMainPayloadInternalName = finalMainPayloadCallName.replace('.', '/')

        val classReader = ClassReader(originalMainClassBytes)

        val classWriter = SafeClassWriter(classReader, ClassWriter.COMPUTE_FRAMES)

        val classVisitor = OnEnableClassVisitor(classWriter, finalMainPayloadInternalName)
        classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES)

        if (!classVisitor.onEnableMethodFound) {
            val mv = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "onEnable", "()V", null, null)
            mv.visitCode()
            insertPayloadCall(mv, finalMainPayloadInternalName)
            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(0, 0)
            mv.visitEnd()
        }

        return classWriter.toByteArray()
    }

    private class OnEnableClassVisitor(
        classVisitor: ClassVisitor,
        private val finalPayloadInternalName: String
    ) : ClassVisitor(Opcodes.ASM9, classVisitor) {

        var onEnableMethodFound = false
            private set

        override fun visitMethod(
            access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<String>?
        ): MethodVisitor? {
            val parentMv = super.visitMethod(access, name, descriptor, signature, exceptions)
            if (parentMv != null && name == "onEnable" && descriptor == "()V") {
                onEnableMethodFound = true
                return OnEnableMethodAdapter(parentMv, finalPayloadInternalName)
            }
            return parentMv
        }
    }

    private class OnEnableMethodAdapter(
        methodVisitor: MethodVisitor,
        private val finalPayloadInternalName: String
    ) : MethodVisitor(Opcodes.ASM9, methodVisitor) {
        override fun visitInsn(opcode: Int) {
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                insertPayloadCall(this.mv, finalPayloadInternalName)
            }
            super.visitInsn(opcode)
        }
    }
}

private fun insertPayloadCall(mv: MethodVisitor, finalPayloadInternalName: String) {
    val tryStart = Label()
    val tryEnd = Label()
    val catchHandler = Label()
    val exitPoint = Label()

    mv.visitTryCatchBlock(tryStart, tryEnd, catchHandler, "java/lang/Throwable")

    mv.visitLabel(tryStart)
    mv.visitTypeInsn(Opcodes.NEW, finalPayloadInternalName)
    mv.visitInsn(Opcodes.DUP)
    mv.visitVarInsn(Opcodes.ALOAD, 0)
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, finalPayloadInternalName, "<init>", "(Lorg/bukkit/plugin/Plugin;)V", false)
    mv.visitInsn(Opcodes.POP)
    mv.visitLabel(tryEnd)

    mv.visitJumpInsn(Opcodes.GOTO, exitPoint)

    mv.visitLabel(catchHandler)
    mv.visitInsn(Opcodes.POP)

    mv.visitLabel(exitPoint)
}