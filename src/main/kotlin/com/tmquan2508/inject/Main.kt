package com.tmquan2508.inject

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.tmquan2508.inject.cli.Logs
import com.tmquan2508.inject.cli.findArg
import com.tmquan2508.inject.cli.boolArg
import com.tmquan2508.inject.cli.HELP_MESSAGE
import com.tmquan2508.inject.config.Config
import com.tmquan2508.inject.core.JarPatcher
import java.io.File
import java.nio.file.Files
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    try {
        runCli(args.toList())
    } catch (e: Exception) {
        handleError(e, boolArg("trace-errors", "tr", args.toList()))
    }
}

fun runCli(args: List<String>) {
    if (args.isEmpty() || boolArg("help", "h", args)) showHelpAndExit()

    val command = args.first()
    val commandArgs = args.drop(1)

    Logs.debugEnabled = boolArg("debug", "db", args)

    when (command) {
        "--inject" -> runInjection(commandArgs)
        "--generate-config" -> runGenerateConfig(commandArgs)
        else -> {
            Logs.error("Unknown command: $command"); println(HELP_MESSAGE); exitProcess(1)
        }
    }
}

fun runGenerateConfig(args: List<String>) {
    val outputPath = args.firstOrNull() ?: throw Exception("Please provide the output path for config.json")
    try {
        val defaultConfig = Config()
        val jsonString = GsonBuilder().setPrettyPrinting().create().toJson(defaultConfig)
        File(outputPath).writeText(jsonString, Charsets.UTF_8)
        Logs.info("Default configuration file created at: $outputPath")
    } catch (e: Exception) {
        throw Exception("Could not write default configuration file: ${e.message}", e)
    }
}

fun runInjection(args: List<String>) {
    val configPath = findArg("config", "c", args)
        ?: throw Exception("Please provide the path to the configuration file via --config <path>")
    val config = loadConfig(configPath)

    val mode = findArg("mode", "m", args) ?: "single"
    val inputPath = findArg("input", "i", args) ?: if (mode == "multiple") "in" else "in.jar"
    val outputPath = findArg("output", "o", args) ?: if (mode == "multiple") "out" else "out.jar"
    val replace = boolArg("replace", "r", args)
    val camouflage = boolArg("camouflage", null, args)

    val inputFiles = when (mode) {
        "multiple" -> {
            val inputDir = File(inputPath)
            if (!inputDir.isDirectory) throw Exception("Input path is not a directory for 'multiple' mode: $inputPath")
            inputDir.listFiles { _, name -> name.endsWith(".jar") }?.toList() ?: emptyList()
        }
        "single" -> {
            val inputFile = File(inputPath)
            if (!inputFile.isFile) throw Exception("Input path is not a file for 'single' mode: $inputPath")
            listOf(inputFile)
        }
        else -> throw Exception("Invalid mode: $mode. Use 'single' or 'multiple'.")
    }

    if (inputFiles.isEmpty()) {
        Logs.warn("No .jar files found in the input path.")
        return
    }

    Logs.info("Found ${inputFiles.size} JAR(s) to process in '$mode' mode.")

    inputFiles.forEach { inputFile ->
        val effectiveOutputPath = when (mode) {
            "multiple" -> {
                val outputDir = File(outputPath).apply { mkdirs() }
                outputDir.resolve(inputFile.name).toPath()
            }
            else -> File(outputPath).toPath()
        }

        if (!replace && Files.exists(effectiveOutputPath)) {
            Logs.warn("Skipped because output file already exists: ${effectiveOutputPath.fileName}. Use --replace to overwrite.")
            return@forEach
        }

        JarPatcher(
            inputPath = inputFile.toPath(),
            outputPath = effectiveOutputPath,
            camouflage = camouflage,
            config = config
        ).patch()
    }
}

fun loadConfig(path: String): Config {
    val configFile = File(path)
    if (!configFile.exists()) throw Exception("Configuration file not found: $path")
    return Gson().fromJson(configFile.readText(), Config::class.java)
}

fun handleError(e: Exception, trace: Boolean) {
    if (Logs.task) Logs.finish()
    Logs.error("${e.javaClass.simpleName}: ${e.message}")
    if (trace) {
        e.printStackTrace()
    }
    exitProcess(1)
}

fun showHelpAndExit() {
    println(HELP_MESSAGE)
    exitProcess(0)
}