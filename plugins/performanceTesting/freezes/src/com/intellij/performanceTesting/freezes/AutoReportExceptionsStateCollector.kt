// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performanceTesting.freezes

import com.intellij.diagnostic.ExceptionAutoReportUtil
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector

internal class AutoReportExceptionsStateCollector : ApplicationUsagesCollector() {
  private val GROUP = EventLogGroup("exceptions.auto.report", 2)

  override fun getGroup(): EventLogGroup = GROUP

  private val FIELD_ENABLED = EventFields.Boolean("enabled")
  private val STATE_EVENT = GROUP.registerEvent("state.of.reporting", FIELD_ENABLED)

  override suspend fun getMetricsAsync(): Set<MetricEvent> {
    val enabled = ExceptionAutoReportUtil.isAutoReportEnabled()
    return setOf(STATE_EVENT.metric(enabled))
  }
}