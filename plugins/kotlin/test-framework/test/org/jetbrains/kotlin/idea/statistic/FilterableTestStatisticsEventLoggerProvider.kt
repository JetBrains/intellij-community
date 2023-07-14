// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.statistic

import com.intellij.internal.statistic.TestStatisticsEventLogger
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import java.util.concurrent.CompletableFuture

class FilterableTestStatisticsEventLoggerProvider(recorder: String = "FUS", eventIdFilter: (String) -> Boolean) : StatisticsEventLoggerProvider(recorder, 1, DEFAULT_SEND_FREQUENCY_MS, DEFAULT_MAX_FILE_SIZE_BYTES) {
    override val logger: TestStatisticsEventLogger = object : TestStatisticsEventLogger() {
        override fun logAsync(group: EventLogGroup, eventId: String, data: Map<String, Any>, isState: Boolean): CompletableFuture<Void> {
            if (!eventIdFilter(eventId)) return CompletableFuture.completedFuture(null)
            return super.logAsync(group, eventId, data, isState)
        }
    }

    override fun isRecordEnabled(): Boolean = true

    override fun isSendEnabled(): Boolean = false

    fun getLoggedEvents(): List<LogEvent> = logger.logged
}
