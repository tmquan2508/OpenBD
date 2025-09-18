package com.tmquan2508.inject.cli

import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*
import com.tmquan2508.inject.log.BDLogger

class ConsoleLogger : BDLogger {
    private var debugEnabled: Boolean = false
    private var taskActive = false
    private var taskFinish = false

    override fun setDebug(enabled: Boolean) {
        this.debugEnabled = enabled
    }

    override fun task(msg: String) {
        println(" ${brightMagenta("╓")} ${brightGreen(bold(msg))}")
        taskActive = true
    }

    override fun finish(): BDLogger {
        taskFinish = true
        return this
    }

    private fun log(message: String) {
        if (taskActive) {
            if (taskFinish) {
                taskFinish = false
                taskActive = false
                println(" ${brightMagenta("╙")} $message")
            } else {
                println(" ${brightMagenta("║")} $message")
            }
        } else {
            println(message)
        }
    }

    override fun info(message: String) = log(brightWhite(message))
    override fun warn(message: String) = log(brightYellow(message))
    override fun error(message: String) = log(brightRed(message))
    override fun debug(message: String) {
        if (debugEnabled) {
            log(gray(message))
        }
    }
}