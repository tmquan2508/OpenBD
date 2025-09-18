package com.tmquan2508.inject.core.analysis

import org.yaml.snakeyaml.Yaml
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.inputStream

data class TargetJarInfo(
    val mainClassName: String,
    val mainClassPath: String,
    val mainClassBytes: ByteArray
)

class JarAnalyzer {
    fun analyze(jarPath: Path): TargetJarInfo {
        lateinit var mainClassName: String
        lateinit var mainClassPath: String
        lateinit var mainClassBytes: ByteArray

        FileSystems.newFileSystem(jarPath).use { fs ->
            val pluginYmlPath = fs.getPath("/plugin.yml")
            if (!Files.exists(pluginYmlPath)) {
                throw Exception("Required 'plugin.yml' not found in the JAR file.")
            }

            val pluginYml: Map<String, Any> = Yaml().load(pluginYmlPath.inputStream())
            mainClassName = pluginYml["main"] as? String
                ?: throw Exception("Could not find 'main' class definition in plugin.yml")

            mainClassPath = mainClassName.replace('.', '/') + ".class"
            val mainClassInJarPath = fs.getPath(mainClassPath)

            if (!Files.exists(mainClassInJarPath)) {
                throw Exception("Main class file not found at path: $mainClassPath")
            }

            mainClassBytes = Files.readAllBytes(mainClassInJarPath)
        }

        return TargetJarInfo(mainClassName, mainClassPath, mainClassBytes)
    }
}