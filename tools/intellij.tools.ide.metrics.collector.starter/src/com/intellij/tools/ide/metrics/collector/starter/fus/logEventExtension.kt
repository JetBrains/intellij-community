package com.intellij.tools.ide.metrics.collector.starter.fus

import com.intellij.internal.statistic.eventLog.LogEventSerializer
import com.jetbrains.fus.reporting.model.lion3.LogEvent

fun LogEvent.print(): String {
  return LogEventSerializer.toString(this)
}