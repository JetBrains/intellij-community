// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project

class PyPackageInEditorUsageCollector : ProjectUsagesCollector() {
  override fun getMetrics(project: Project): Set<MetricEvent> {
    val keys = PyPackageUsageStatistics.getInstance(project).getStatisticsAndResetState().keys
    return keys.map { packageUsage ->
      PYTHON_PACKAGES_IN_EDITOR.metric(PACKAGE_FIELD.with(packageUsage.name),
                                       PACKAGE_VERSION_FIELD.with(packageUsage.name),
                                       INTERPRETER_TYPE.with(packageUsage.interpreterTypeValue),
                                       EXECUTION_TYPE.with(packageUsage.targetTypeValue),
                                       HAS_SDK.with(packageUsage.hasSdk!!)
                                       )

    }.toSet()
  }

  override fun requiresReadAccess(): Boolean = false

  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("python.packages.in.editor", 1)
  private val HAS_SDK = EventFields.Boolean("has_sdk")
  private val PYTHON_PACKAGES_IN_EDITOR = GROUP.registerVarargEvent("python.packages.used",
                                                                    PACKAGE_FIELD,
                                                                    PACKAGE_VERSION_FIELD,
                                                                    EXECUTION_TYPE,
                                                                    INTERPRETER_TYPE,
                                                                    HAS_SDK)
}