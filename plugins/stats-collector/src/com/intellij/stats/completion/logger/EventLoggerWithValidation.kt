/*
 * Copyright 2000-2020 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.stats.completion.logger

import com.intellij.openapi.Disposable
import com.intellij.stats.completion.tracker.CompletionEventLogger
import com.intellij.stats.completion.LogEventSerializer
import com.intellij.stats.completion.events.LogEvent
import com.intellij.util.concurrency.SequentialTaskExecutor

/**
 * @author Vitaliy.Bibaev
 */
class EventLoggerWithValidation(private val fileLogger: FileLogger, private val validator: SessionValidator)
    : CompletionEventLogger, Disposable {
    private val taskExecutor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("Completion Events Log Executor")
    private val session: MutableList<LogEvent> = mutableListOf()

    override fun log(event: LogEvent) {
        if (session.isEmpty() || event.sessionUid == session.first().sessionUid) {
            session.add(event)
        } else {
            validateAndLogInBackground(false)
            session.add(event)
        }
    }

    override fun dispose() {
        validateAndLogInBackground(true)
    }

    private fun validateAndLogInBackground(flush: Boolean) {
        val lastSession = session.toList()
        taskExecutor.execute {
            validateAndLog(lastSession)
            if (flush) {
                fileLogger.flush()
            }
        }
        session.clear()
    }

    private fun validateAndLog(session: List<LogEvent>) {
        validator.validate(session)
        val lines = session.map { LogEventSerializer.toString(it) }
        fileLogger.printLines(lines)
    }
}