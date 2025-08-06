package com.tmquan2508.inject.utils

import com.tmquan2508.inject.cli.Logs
import com.rikonardo.cafebabe.ClassFile
import java.io.File
import java.io.IOException
import java.net.JarURLConnection
import java.util.jar.JarFile

fun loadDefaultPayload(): List<ClassFile> {
    Logs.info("Loading default payload from resources...")
    val payloadClasses = mutableListOf<ClassFile>()
    val defaultPackagePath = "com/tmquan2508/exploit"

    try {
        val classLoader = Thread.currentThread().contextClassLoader
        val resourceUrl = classLoader.getResource(defaultPackagePath)
            ?: throw IOException("Could not find default payload directory '$defaultPackagePath' in resources.")

        if (resourceUrl.protocol == "jar") {
            val connection = resourceUrl.openConnection() as JarURLConnection
            val jarFile: JarFile = connection.jarFile
            jarFile.entries().asSequence()
                .filter { it.name.startsWith(defaultPackagePath) && it.name.endsWith(".class") && !it.isDirectory }
                .forEach { entry ->
                    jarFile.getInputStream(entry).use { inputStream ->
                        payloadClasses.add(ClassFile(inputStream.readBytes()))
                    }
                }
        } else {
            val dir = File(resourceUrl.toURI())
            dir.walk()
                .filter { it.isFile && it.extension == "class" }
                .forEach { file ->
                    payloadClasses.add(ClassFile(file.readBytes()))
                }
        }
    } catch (e: Exception) {
        throw Exception("Failed to load default payload from resources: ${e.message}", e)
    }

    if (payloadClasses.isEmpty()) {
        throw Exception("No payload classes were found. Ensure .class files are present in the resources under '$defaultPackagePath'")
    }

    Logs.info("Successfully loaded ${payloadClasses.size} classes from the default payload.")
    return payloadClasses
}