// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.statistics

import com.intellij.internal.statistic.service.fus.collectors.FUSProjectUsageTrigger
import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsageTriggerCollector
import com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project

class MavenActionsUsagesCollector : ProjectUsageTriggerCollector() {
  override fun getGroupId() = "statistics.build.maven.actions"

  companion object {
    @JvmStatic
    fun trigger(project: Project?, action: AnAction, event: AnActionEvent?) {
      trigger(project, action, event, emptyMap())
    }

    @JvmStatic
    fun trigger(project: Project?, action: AnAction, event: AnActionEvent?, data: Map<String, String> = emptyMap()) {
      if (project == null) return
      val context = FUSUsageContext.create()
      if (event != null) {
        context.data["place"] = event.place
        context.data["isFromContextMenu"] = event.isFromContextMenu.toString()
      }
      context.data.putAll(data)
      val actionClassName = UsageDescriptorKeyValidator.ensureProperKey(action.javaClass.simpleName)
      FUSProjectUsageTrigger.getInstance(project).trigger(MavenActionsUsagesCollector::class.java, actionClassName, context)
    }

    @JvmStatic
    fun trigger(project: Project?, feature: String) {
      trigger(project, feature, emptyMap())
    }

    @JvmStatic
    fun trigger(project: Project?, feature: String, data: Map<String, String> = emptyMap()) {
      if (project == null) return
      val context = FUSUsageContext.create()
      context.data.putAll(data)
      FUSProjectUsageTrigger.getInstance(project).trigger(MavenActionsUsagesCollector::class.java, feature, context)
    }
  }
}
