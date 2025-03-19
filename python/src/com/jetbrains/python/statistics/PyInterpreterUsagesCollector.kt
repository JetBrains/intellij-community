// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project

/**
 * Reports sdk usages: version, dialect and remote/local
 */
internal class PyInterpreterUsagesCollector : ProjectUsagesCollector() {
  override fun getMetrics(project: Project) =
    project.sdks.toSet()
      .mapTo(mutableSetOf()) { sdk ->
        PYTHON_SDK_USED.metric(getPythonSpecificInfo(sdk))
      }

  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("python.sdks", 5)
  private val PYTHON_SDK_USED = registerPythonSpecificEvent(GROUP, "python_sdk_used")
}

