// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.statistics.feedback


import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.eventLog.events.EventId2

object PythonJobStatisticsCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("python.job.statistics", 2)
  private val USE_FOR = EventFields.StringList("use_for", listOf("data_analysis", "ml", "web_dev", "scripts"))
  private val OTHER = EventFields.StringValidatedByInlineRegexp("other", "[\\w\\s,.!?-]{1,25}")
  private val JOB_EVENT: EventId2<List<String>, String?> = GROUP.registerEvent("job.survey.triggered", USE_FOR, OTHER)

  @JvmStatic
  fun logJobEvent(useFor: List<String>, other: String?) {
    JOB_EVENT.log(useFor, other)
  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}
