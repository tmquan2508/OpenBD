package com.tmquan2508.inject.log

interface BDLogger {
    fun task(msg: String)
    fun finish(): BDLogger
    fun info(message: String)
    fun warn(message: String)
    fun error(message: String)
    fun debug(message: String)
    fun setDebug(enabled: Boolean)
}