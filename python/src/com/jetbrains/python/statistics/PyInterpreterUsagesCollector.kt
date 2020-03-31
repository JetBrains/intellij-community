// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project

/**
 * Reports sdk usages: version, dialect and remote/local
 */
class PyInterpreterUsagesCollector : ProjectUsagesCollector() {
  override fun getMetrics(project: Project) =
    project.sdks
      .mapTo(mutableSetOf()) { sdk ->
        MetricEvent(
          "python_sdk_used",
          FeatureUsageData()
            .addPythonSpecificInfo(sdk)
        )
      }

  override fun getGroupId() = "python.sdks"
}

