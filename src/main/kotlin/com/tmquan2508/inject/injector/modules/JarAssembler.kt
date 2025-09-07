package com.tmquan2508.inject.injector.modules

import com.rikonardo.cafebabe.ClassFile
import com.tmquan2508.inject.cli.Logs
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

internal fun assembleFinalJar(
    jarPath: Path,
    modifiedMainClassBytes: ByteArray,
    mainClassPath: String,
    finalPayloadClasses: List<ClassFile>
) {
    FileSystems.newFileSystem(jarPath).use { fs ->
        Logs.info(" -> Injecting ${finalPayloadClasses.size} payload classes...")
        finalPayloadClasses.forEach { finalClass ->
            val pathInJar = fs.getPath(finalClass.name + ".class")
            pathInJar.parent?.let { if (!Files.exists(it)) Files.createDirectories(it) }
            Files.write(pathInJar, finalClass.compile())
        }
        Logs.info(" -> Replacing original main class with patched version...")
        val pathInJar = fs.getPath(mainClassPath)
        Files.write(pathInJar, modifiedMainClassBytes)
    }
}