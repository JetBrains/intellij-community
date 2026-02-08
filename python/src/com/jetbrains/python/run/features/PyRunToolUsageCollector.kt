// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.features

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.project.Project

/**
 * FUS collector for "Run with …" Python tools exposed via [PyRunToolProvider].
 * Logs how many runs are started via a run tool and the tool id.
 */
internal object PyRunToolUsageCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("python.run.tool", 1)

  private val TOOL_ID = EventFields.StringValidatedByCustomRule("run_tool_id", PyRunToolIdValidator::class.java)

  private val RUN_USED: EventId1<String?> = GROUP.registerEvent("run.used", TOOL_ID)

  @JvmStatic
  fun logRun(project: Project, runToolId: String) {
    RUN_USED.log(project, runToolId)
  }

  override fun getGroup(): EventLogGroup = GROUP
}

/**
 * Custom validator for PyRunTool IDs in FUS statistics.
 * Validates that the ID corresponds to a known PyRunToolProvider.
 */
internal class PyRunToolIdValidator : CustomValidationRule() {
  override fun getRuleId(): String = "python_run_tool"

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    val validIds = PyRunToolProvider.EP.extensionList
      .asSequence()
      .filter { getPluginInfo(it.javaClass).isDevelopedByJetBrains() }
      .map { it.runToolData.idForStatistics }
      .toSet()

    return if (data in validIds) ValidationResultType.ACCEPTED else ValidationResultType.REJECTED
  }
}
