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
    val className: String
)

class Camouflage {
    private val CAMEL_CASE_SPLIT_REGEX = "(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])".toRegex()
    private val STRUCTURAL_SPLIT_REGEX = "^([A-Z][a-z]+(?:[A-Z][a-z]+)*)([0-9].*)$".toRegex()
    private val OBFUSCATION_PATTERN_REGEX = "^([a-zA-Z0-9]+)_([a-zA-Z0-9]+)$".toRegex()
    private val COMMON_SUFFIXES = listOf("Task", "Worker", "Service", "Manager", "Provider", "Handler", "Util", "Core")

    fun generatePlan(targetJarFile: File): CamouflagePlan {
        val packageStructure = scanJarForPackageStructure(targetJarFile)
        if (packageStructure.isEmpty()) {
            return CamouflagePlan("com/tmquan2508/internal/safe", "InternalTask")
        }

        val packagesSorted = packageStructure.entries
            .filter { it.key.isNotEmpty() && !it.key.startsWith("META-INF") }
            .sortedByDescending { it.value.size }

        if (packagesSorted.isEmpty()) {
            return CamouflagePlan("com/tmquan2508/internal/fallback", "Task")
        }

        val chosenEntry = packagesSorted.take(5).random()
        val chosenPackagePath = chosenEntry.key
        val simpleNames = chosenEntry.value.map { it.substringBeforeLast(".") }

        Logs.debug(" -> Selected package for camouflage: '$chosenPackagePath'")

        val obfuscatedName = tryObfuscationPatternStrategy(simpleNames)
        if (obfuscatedName != null) {
            Logs.debug(" -> Obfuscation Pattern strategy succeeded.")
            return CamouflagePlan(chosenPackagePath, obfuscatedName)
        }

        val structuralName = tryStructuralPatternStrategy(simpleNames)
        if (structuralName != null) {
            Logs.debug(" -> Structural Pattern strategy succeeded.")
            return CamouflagePlan(chosenPackagePath, structuralName)
        }

        val contextualName = tryContextualChainStrategy(simpleNames)
        if (contextualName != null) {
            Logs.debug(" -> Contextual Chaining strategy succeeded.")
            return CamouflagePlan(chosenPackagePath, contextualName)
        }

        Logs.warn("Advanced strategies failed. Using robust dictionary fallback.")
        val dictionary = buildWordDictionaryFrom(simpleNames)
        if (dictionary.isEmpty()) {
            val fallbackName = simpleNames.firstOrNull() ?: "Fallback"
            return CamouflagePlan(chosenPackagePath, findUniqueNameWithSuffixOrNumber(fallbackName, simpleNames))
        }
        val fallbackName = buildUniqueWordName(dictionary, simpleNames, 1, 2)

        return CamouflagePlan(chosenPackagePath, fallbackName)
    }

    private fun tryObfuscationPatternStrategy(simpleNames: List<String>): String? {
        val baseCounts = simpleNames
            .mapNotNull { OBFUSCATION_PATTERN_REGEX.find(it)?.groupValues?.get(1) }
            .groupingBy { it }
            .eachCount()

        if (baseCounts.isEmpty()) return null

        val (dominantBase, count) = baseCounts.maxByOrNull { it.value } ?: return null

        if (count.toDouble() / simpleNames.size > 0.7) {
            Logs.debug(" -> Found a strong obfuscation pattern with base '$dominantBase'.")
            repeat(200) {
                val randomSuffix = (1..2).map { ('a'..'z').random() }.joinToString("")
                val newName = "${dominantBase}_$randomSuffix"
                if (newName !in simpleNames) {
                    return newName
                }
            }
        }
        return null
    }

    private fun tryStructuralPatternStrategy(simpleNames: List<String>): String? {
        val structuralPatterns = mutableMapOf<String, MutableSet<String>>()
        simpleNames.forEach { name ->
            STRUCTURAL_SPLIT_REGEX.find(name)?.let { matchResult ->
                val (_, base, descriptor) = matchResult.groupValues
                structuralPatterns.getOrPut(base) { mutableSetOf() }.add(descriptor)
            }
        }
        val dominantBases = structuralPatterns.filter { (_, suffixes) -> suffixes.size >= 2 }
        val coveredClasses = dominantBases.values.sumOf { it.size } + dominantBases.keys.size
        if (dominantBases.isNotEmpty() && (coveredClasses.toDouble() / simpleNames.size) > 0.6) {
            Logs.debug(" -> Found a strong structural pattern.")
            repeat(200) {
                val (baseToUse, availableSuffixes) = dominantBases.entries.random()
                val randomSuffix = availableSuffixes.random()
                val newName = baseToUse + randomSuffix
                if (newName !in simpleNames) return newName
            }
        }
        return null
    }

    private fun tryContextualChainStrategy(simpleNames: List<String>): String? {
        val nameParts = simpleNames.map { CAMEL_CASE_SPLIT_REGEX.split(it).filter(String::isNotEmpty) }
        val contextMap = mutableMapOf<String, MutableList<String>>()
        val startWords = mutableSetOf<String>()
        nameParts.forEach { parts ->
            if (parts.isNotEmpty()) {
                startWords.add(parts.first())
                for (i in 0 until parts.size - 1) {
                    contextMap.getOrPut(parts[i]) { mutableListOf() }.add(parts[i + 1])
                }
            }
        }
        if (startWords.isEmpty()) return null
        val avgWordCount = nameParts.map { it.size }.average()
        val minWords = maxOf(1, (avgWordCount - 1).toInt())
        val maxWords = (avgWordCount + 1).toInt()

        repeat(100) {
            val wordCount = if (maxWords > minWords) Random.nextInt(minWords, maxWords + 1) else minWords
            val nameChain = mutableListOf<String>()
            var currentWord = startWords.random()
            nameChain.add(currentWord)
            while (nameChain.size < wordCount) {
                val nextWords = contextMap[currentWord]
                if (nextWords.isNullOrEmpty()) break
                currentWord = nextWords.random()
                nameChain.add(currentWord)
            }
            val newName = nameChain.joinToString("") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
            if (newName.isNotEmpty() && newName !in simpleNames) {
                return newName
            }
        }

        Logs.debug(" -> Contextual chain generation failed, trying suffix fallback...")
        if (startWords.isNotEmpty()) {
            val baseName = startWords.random()
            return findUniqueNameWithSuffixOrNumber(baseName, simpleNames)
        }
        return null
    }

    private fun buildUniqueWordName(dictionary: List<String>, existingNames: List<String>, minWords: Int, maxWords: Int): String {
        repeat(200) {
            val wordCount = if (maxWords > minWords) Random.nextInt(minWords, maxWords + 1) else minWords
            val nameBuilder = StringBuilder()
            var lastWord: String? = null

            for (i in 1..wordCount) {
                var word: String
                var wordAttempt = 0
                do {
                    word = dictionary.random()
                    wordAttempt++
                } while (word == lastWord && dictionary.size > 1 && wordAttempt < 10)

                val capitalizedWord = word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                nameBuilder.append(capitalizedWord)
                lastWord = word
            }

            val newName = nameBuilder.toString()
            if (newName.isNotEmpty() && newName !in existingNames) {
                return newName
            }
        }

        return findUniqueNameWithSuffixOrNumber(dictionary.random(), existingNames)
    }

    private fun findUniqueNameWithSuffixOrNumber(baseName: String, existingNames: List<String>): String {
        for (suffix in COMMON_SUFFIXES.shuffled()) {
            val newName = baseName + suffix
            if (newName !in existingNames) {
                return newName
            }
        }

        for (i in 1..100) {
            val newName = baseName + i
            if (newName !in existingNames) {
                return newName
            }
        }

        return baseName + Random.nextInt(101, 999)
    }

    private fun scanJarForPackageStructure(jarFile: File): Map<String, List<String>> {
        val packageMap = mutableMapOf<String, MutableList<String>>()
        try {
            ZipFile(jarFile).use { zipFile ->
                val zipEntries: Enumeration<out ZipEntry> = zipFile.entries()
                while (zipEntries.hasMoreElements()) {
                    val entry = zipEntries.nextElement()
                    val entryName = entry.name
                    if (!entry.isDirectory &&
                        entryName.endsWith(".class") &&
                        !entryName.contains("$") &&
                        !entryName.endsWith("package-info.class") &&
                        entryName != "module-info.class") {
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
        .flatMap { CAMEL_CASE_SPLIT_REGEX.split(it) }
        .map { it.replace(Regex("[^a-zA-Z0-9]"), "") }
        .filter { it.length > 2 }
        .distinct()
}