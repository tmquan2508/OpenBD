package com.tmquan2508.inject.injector.modules

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.inputStream

data class TargetJarInfo(
    val mainClassName: String,
    val mainClassPath: String,
    val mainClassBytes: ByteArray
)

internal fun analyzeTargetJar(jarPath: Path): TargetJarInfo {
    lateinit var mainClassName: String
    lateinit var mainClassBytes: ByteArray
    lateinit var mainClassPath: String

    FileSystems.newFileSystem(jarPath).use { fs ->
        val pluginYml: Map<String, Any> = org.yaml.snakeyaml.Yaml().load(fs.getPath("/plugin.yml").inputStream())
        mainClassName = pluginYml["main"] as? String ?: throw Exception("No main class in plugin.yml")
        mainClassPath = mainClassName.replace('.', '/') + ".class"
        mainClassBytes = Files.readAllBytes(fs.getPath(mainClassPath))
    }
    
    return TargetJarInfo(mainClassName, mainClassPath, mainClassBytes)
}