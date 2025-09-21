package com.tmquan2508.inject

import com.google.gson.Gson
import com.tmquan2508.inject.config.Config
import com.tmquan2508.inject.core.JarPatcher
import com.tmquan2508.inject.log.BDLogger
import java.nio.file.Path

object PatcherFacade {
    fun patchJar(
        inputFile: Path,
        outputFile: Path,
        configJson: String,
        downloaderUrl: String,
        logger: BDLogger
    ): Boolean {
        val config = Gson().fromJson(configJson, Config::class.java)
            ?: throw IllegalArgumentException("Could not parse configuration JSON.")

        val patcher = JarPatcher(
            inputPath = inputFile,
            outputPath = outputFile,
            config = config,
            configJson = configJson,
            downloaderUrl = downloaderUrl,
            logger = logger
        )

        return patcher.patch()
    }
}