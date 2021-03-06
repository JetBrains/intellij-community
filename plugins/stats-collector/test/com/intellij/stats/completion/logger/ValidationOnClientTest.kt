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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.stats.completion.*
import com.intellij.stats.completion.events.DownPressedEvent
import com.intellij.stats.completion.events.LogEvent
import com.intellij.testFramework.HeavyPlatformTestCase
import junit.framework.TestCase
import java.util.concurrent.LinkedBlockingQueue

/**
 * @author Vitaliy.Bibaev
 */
class ValidationOnClientTest : HeavyPlatformTestCase() {
    private companion object {
        val EMPTY_STATE = LookupState(emptyList(), emptyList(), emptyList(), 1, emptyMap())
        const val bucket = "0"
        const val language = "java"
    }

    fun `test validation before log`() {
        val event1 = DownPressedEvent("1", "1", EMPTY_STATE, bucket, System.currentTimeMillis(), language)
        val event2 = DownPressedEvent("1", "2", EMPTY_STATE, bucket, System.currentTimeMillis(), language)

        TestCase.assertEquals(ValidationStatus.UNKNOWN, event1.validationStatus)
        val queue = LinkedBlockingQueue<DeserializedLogEvent>()
        val logger = createLogger { queue.addAll(it) }

        logger.log(event1)
        logger.log(event2)

        val event = queue.take().event!!
        TestCase.assertEquals(ValidationStatus.VALID, event.validationStatus)
    }

    fun `test log after session finished`() {
        val event1 = DownPressedEvent("1", "1", EMPTY_STATE, bucket, System.currentTimeMillis(), language)
        val event2 = DownPressedEvent("1", "1", EMPTY_STATE.withSelected(2), bucket, System.currentTimeMillis(), language)
        val event3 = DownPressedEvent("1", "2", EMPTY_STATE, bucket, System.currentTimeMillis(), language)

        val queue = LinkedBlockingQueue<DeserializedLogEvent>()

        val logger = createLogger { queue.addAll(it) }

        logger.log(event1)
        TestCase.assertTrue(queue.isEmpty())

        logger.log(event2)
        TestCase.assertTrue(queue.isEmpty())
        logger.log(event3)

        val e1 = queue.take().event!!
        TestCase.assertEquals(event1.sessionUid, e1.sessionUid)
        val e2 = queue.take().event!!
        TestCase.assertEquals(event2.sessionUid, e2.sessionUid)
        TestCase.assertTrue(queue.isEmpty())
    }

    fun `test log executed on pooled thread`() {
        val event1 = DownPressedEvent("1", "1", EMPTY_STATE, bucket, System.currentTimeMillis(), language)
        val event2 = DownPressedEvent("1", "2", EMPTY_STATE, bucket, System.currentTimeMillis(), language)

        val queue = LinkedBlockingQueue<Boolean>()
        val logger = createLogger { queue.add(ApplicationManager.getApplication().isDispatchThread) }
        logger.log(event1)
        logger.log(event2)

        TestCase.assertFalse(queue.take())
    }

    private class DefaultValidator : SessionValidator {
        override fun validate(session: List<LogEvent>) {
            session.forEach { it.validationStatus = ValidationStatus.VALID }
        }
    }

    private fun createLogger(onLogCallback: (List<DeserializedLogEvent>) -> Unit): EventLoggerWithValidation {
        return EventLoggerWithValidation(object : FileLogger {

            override fun printLines(lines: List<String>) {
                val session = lines.map { LogEventSerializer.fromString(it) }
                onLogCallback(session)
            }

            override fun flush() = Unit
        }, DefaultValidator())
    }
}