// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.test.util

import org.jetbrains.kotlin.idea.debugger.evaluate.KotlinDebuggerCaches
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

internal class LogPropagator(val systemLogger: (String) -> Unit) {
    private var oldLogLevel: Level? = null
    private val logger = Logger.getLogger('#' + KotlinDebuggerCaches::class.java.name)
    private var appender: Handler? = null

    fun attach() {
        oldLogLevel = logger.level
        logger.level = Level.FINE

        appender = object : Handler() {
            override fun publish(record: LogRecord) {
                val message = record.message
                if (message != null) {
                    systemLogger(message)
                }
            }

            override fun flush() {
            }

            override fun close() {
            }
        }

        logger.addHandler(appender)
    }

    fun detach() {
        logger.removeHandler(appender)
        appender = null

        logger.level = oldLogLevel
    }
}