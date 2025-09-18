package com.tmquan2508.inject.core.analysis

import com.tmquan2508.inject.log.BDLogger
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.Enumeration
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

data class JavaVersion(val major: Int, val minor: Int = 0)

class JavaVersionScanner(private val logger: BDLogger) {
    fun findDominantVersion(jarPath: Path): JavaVersion {
        val counts = mutableMapOf<JavaVersion, Int>()
        try {
            ZipFile(jarPath.toFile()).use { zipFile ->
                val entries: Enumeration<out ZipEntry> = zipFile.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.endsWith(".class")) {
                        zipFile.getInputStream(entry).use { inStream ->
                            val header = ByteArray(8)
                            if (inStream.read(header) == 8 && ByteBuffer.wrap(header, 0, 4).int == 0xCAFEBABE.toInt()) {
                                val minor = ByteBuffer.wrap(header, 4, 2).short.toInt() and 0xFFFF
                                val major = ByteBuffer.wrap(header, 6, 2).short.toInt() and 0xFFFF
                                val version = JavaVersion(major, minor)
                                counts[version] = counts.getOrDefault(version, 0) + 1
                            }
                        }
                    }
                }
            }
        } catch (e: IOException) {
            logger.warn("[Patcher] Could not scan for class versions: ${e.message}. Defaulting to Java 8.")
            return JavaVersion(52)
        }

        return counts.entries.maxByOrNull { it.value }?.key ?: JavaVersion(52)
    }

    companion object {
        fun setClassVersion(classBytes: ByteArray, newVersion: JavaVersion) {
            if (classBytes.size < 8) return

            classBytes[4] = (newVersion.minor shr 8).toByte()
            classBytes[5] = newVersion.minor.toByte()

            classBytes[6] = (newVersion.major shr 8).toByte()
            classBytes[7] = newVersion.major.toByte()
        }
    }
}