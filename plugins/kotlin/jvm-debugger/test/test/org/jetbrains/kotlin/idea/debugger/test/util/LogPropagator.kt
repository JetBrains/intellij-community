// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.test.util

import org.jetbrains.kotlin.idea.debugger.evaluate.KotlinEvaluator
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

internal class LogPropagator(val systemLogger: (String) -> Unit) {
    private companion object {
        private val LOG = Logger.getLogger('#' + KotlinEvaluator::class.java.name)
    }

    private var oldLogLevel: Level? = null
    private var appender: Handler? = null

    fun attach() {
        oldLogLevel = LOG.level
        LOG.level = Level.FINE

        appender = object : Handler() {
            override fun publish(record: LogRecord) {
                val message = record.message ?: return
                systemLogger(message)
            }

            override fun flush() {}
            override fun close() {}
        }

        LOG.addHandler(appender)
    }

    fun detach() {
        LOG.removeHandler(appender)
        appender = null
        LOG.level = oldLogLevel
    }
}