package com.tmquan2508.inject.util

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object StaticKeyEncoder {
    private const val SECRET_KEY = "openbd.secret.key"
    fun encrypt(plainText: String): String {
        val inputBytes = plainText.toByteArray(Charsets.UTF_8)
        val keyBytes = SECRET_KEY.toByteArray(Charsets.UTF_8)
        val outputBytes = ByteArray(inputBytes.size) { i -> (inputBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte() }
        return Base64.getEncoder().encodeToString(outputBytes)
    }
}

fun generateRandomKey(length: Int): String {
    val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    return (1..length).map { chars[SecureRandom().nextInt(chars.length)] }.joinToString("")
}

fun encryptWithRandomKey(plainBytes: ByteArray, key: String): String {
    val keyBytes = key.toByteArray(Charsets.UTF_8)
    val result = ByteArray(plainBytes.size) { i -> (plainBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte() }
    return Base64.getEncoder().encodeToString(result)
}

fun String.toSha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(this.toByteArray())
    .joinToString("") { "%02x".format(it) }