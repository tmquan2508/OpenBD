package com.tmquan2508.inject

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.tmquan2508.inject.cli.Logs
import com.tmquan2508.inject.cli.findArg
import com.tmquan2508.inject.cli.boolArg
import com.tmquan2508.inject.cli.HELP_MESSAGE
import com.tmquan2508.inject.injector.patchPlugin
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    try {
        runCli(args)
    } catch (e: Exception) {
        handleError(e, true)
    }
}

fun runCli(args: Array<String>) {
    if (args.isEmpty()) showHelpAndExit()

    val command = args.first()
    val commandArgs = args.drop(1)

    when (command) {
        "--inject" -> runInjection(commandArgs)
        "--generate-config" -> runGenerateConfig(commandArgs)
        "--help", "-h" -> showHelpAndExit()
        else -> {
            Logs.error("Unknown command: $command"); println(HELP_MESSAGE); exitProcess(1)
        }
    }
}

fun runGenerateConfig(args: List<String>) {
    val outputPath = args.firstOrNull() ?: throw Exception("Please provide the output path for config.json")
    try {
        val defaultConfig = OpenBDConfig()
        val jsonString = GsonBuilder().setPrettyPrinting().create().toJson(defaultConfig)
        File(outputPath).writeText(jsonString, Charsets.UTF_8)
        Logs.info("Default configuration file created at: $outputPath")
        exitProcess(0)
    } catch (e: Exception) {
        handleError(Exception("Could not write default configuration file: ${e.message}"), true)
    }
}

fun runInjection(args: List<String>) {
    val configPath = findArg("config", "c", args)
        ?: throw Exception("Please provide the path to the configuration file via --config <path>")
    val config = loadConfig(configPath)

    val mode = findArg("mode", "m", args) ?: "multiple"
    val inputPath = findArg("input", "i", args) ?: if (mode == "multiple") "plugins_in" else "plugin_in.jar"
    val outputPath = findArg("output", "o", args) ?: if (mode == "multiple") "plugins_out" else "plugin_out.jar"
    val replace = boolArg("replace", "r", args)
    val traceErrors = boolArg("trace-errors", "tr", args)
    val camouflage = boolArg("camouflage", null, args)

    val inputFiles: List<File> = when (mode) {
        "multiple" -> {
            val inputDir = File(inputPath)
            if (!inputDir.isDirectory) throw Exception("Input path is not a directory for multiple mode: $inputPath")
            inputDir.listFiles { _, name -> name.endsWith(".jar") }?.toList() ?: emptyList()
        }
        "single" -> {
            val inputFile = File(inputPath)
            if (!inputFile.isFile) throw Exception("Input path is not a file for single mode: $inputPath")
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
        val outputFile = when (mode) {
            "multiple" -> {
                val outputDir = File(outputPath)
                if (!outputDir.exists()) outputDir.mkdirs()
                outputDir.resolve(inputFile.name)
            }
            "single" -> File(outputPath)
            else -> throw IllegalStateException("Invalid mode reached this point, this should not happen.")
        }

        try {
            patchPlugin(
                input = inputFile.toPath(),
                output = outputFile.toPath(),
                replace = replace,
                camouflage = camouflage,
                config = config
            )
        } catch (e: Exception) {
            handleError(e, traceErrors)
        }
    }
}

fun loadConfig(path: String): OpenBDConfig {
    val configFile = File(path)
    if (!configFile.exists()) throw Exception("Configuration file not found: $path")
    return Gson().fromJson(configFile.readText(), OpenBDConfig::class.java)
}

fun handleError(e: Exception, traceErrors: Boolean) {
    if (Logs.task) Logs.finish()
    Logs.error("${e::class.qualifiedName}: ${e.message}")
    if (traceErrors) {
        val stackTrace = ByteArrayOutputStream().use { buff ->
            PrintStream(buff).use { ps -> e.printStackTrace(ps) }
            buff.toString()
        }
        stackTrace.lines().filter { it.isNotBlank() }.forEach { Logs.error(it) }
    }
    exitProcess(1)
}

fun showHelpAndExit() {
    println(HELP_MESSAGE); exitProcess(0)
}