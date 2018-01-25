/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

package com.intellij.stats.logger

import com.intellij.openapi.application.ApplicationManager
import com.intellij.stats.completion.CompletionEventLogger
import com.intellij.stats.completion.ValidationStatus
import com.intellij.stats.completion.events.DownPressedEvent
import com.intellij.stats.completion.events.LogEvent
import com.intellij.testFramework.PlatformTestCase
import junit.framework.TestCase
import java.util.concurrent.LinkedBlockingQueue

/**
 * @author Vitaliy.Bibaev
 */
class ValidationOnClientTest : PlatformTestCase() {
    fun `test validation before log`() {
        val event1 = DownPressedEvent("1", "1", emptyList(), emptyList(), 1)
        val event2 = DownPressedEvent("1", "2", emptyList(), emptyList(), 1)

        TestCase.assertEquals(ValidationStatus.UNKNOWN, event1.validationStatus)
        val queue = LinkedBlockingQueue<ValidationStatus>()
        val logger = createLogger { queue.add(it.validationStatus) }

        logger.log(event1)
        logger.log(event2)

        val status = queue.take()
        TestCase.assertEquals(ValidationStatus.VALID, status)
    }

    fun `test log after session finished`() {
        val event1 = DownPressedEvent("1", "1", emptyList(), emptyList(), 1)
        val event2 = DownPressedEvent("1", "1", emptyList(), emptyList(), 2)
        val event3 = DownPressedEvent("1", "2", emptyList(), emptyList(), 1)

        val queue = LinkedBlockingQueue<LogEvent>()

        val logger = createLogger { queue.put(it) }

        logger.log(event1)
        TestCase.assertTrue(queue.isEmpty())

        logger.log(event2)
        TestCase.assertTrue(queue.isEmpty())
        logger.log(event3)

        val e1 = queue.take()
        TestCase.assertEquals(event1, e1)
        val e2 = queue.take()
        TestCase.assertEquals(event2, e2)
        TestCase.assertTrue(queue.isEmpty())
    }

    fun `test log executed on pooled thread`() {
        val event1 = DownPressedEvent("1", "1", emptyList(), emptyList(), 1)
        val event2 = DownPressedEvent("1", "2", emptyList(), emptyList(), 1)

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

    private fun createLogger(onLogCallback: (LogEvent) -> Unit): CompletionEventLogger {
        return EventLoggerWithValidation(object : CompletionEventLogger {
            override fun log(event: LogEvent) {
                onLogCallback(event)
            }
        }, DefaultValidator())
    }
}