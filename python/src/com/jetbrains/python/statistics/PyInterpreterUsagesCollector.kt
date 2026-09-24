// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.sdk.backend.getSdkAPI

/**
 * Reports sdk usages: version, dialect and remote/local
 */
internal class PyInterpreterUsagesCollector : ProjectUsagesCollector() {
  // The project model's own interpreters, rather than every module's raw SDK: the latter also answers SDKs built
  // outside the blessed creation path, which carry no PythonSdkAdditionalData and make the reporting below throw
  // (PY-90784).
  override suspend fun collect(project: Project): Set<MetricEvent> =
    project.service<EvoPyProjectModel>().snapshot().interpreters
      .mapTo(mutableSetOf()) { interpreter ->
        PYTHON_SDK_USED.metric(getPythonSpecificInfo(interpreter.getSdkAPI()))
      }

  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("python.sdks", 5)
  private val PYTHON_SDK_USED = registerPythonSpecificEvent(GROUP, "python_sdk_used")
}
