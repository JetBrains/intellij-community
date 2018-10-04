// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal

import com.intellij.internal.statistic.service.fus.collectors.FUSProjectUsageTrigger
import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsageTriggerCollector
import com.intellij.openapi.project.Project

class TerminalUsageTriggerCollector : ProjectUsageTriggerCollector() {
  override fun getGroupId(): String = "statistics.terminal"

  companion object {
    fun trigger(project: Project, featureId: String, context: FUSUsageContext) {
      FUSProjectUsageTrigger.getInstance(project).trigger(TerminalUsageTriggerCollector::class.java, featureId, context)
    }
  }
}