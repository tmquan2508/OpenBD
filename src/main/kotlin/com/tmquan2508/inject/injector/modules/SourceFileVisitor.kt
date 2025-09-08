package com.tmquan2508.inject.injector.modules

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes

internal class SourceFileVisitor(
    classVisitor: ClassVisitor,
    private val newSourceFileName: String
) : ClassVisitor(Opcodes.ASM9, classVisitor) {

    private var sourceAttributeFound = false

    override fun visitSource(source: String?, debug: String?) {
        super.visitSource(newSourceFileName, debug)
        sourceAttributeFound = true
    }

    override fun visitEnd() {
        if (!sourceAttributeFound) {
            super.visitSource(newSourceFileName, null)
        }
        super.visitEnd()
    }
}