// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.statistics

import com.intellij.execution.Executor
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project

private const val GROUP_ID = "build.maven.actions"

class MavenActionsUsagesCollector {
  enum class ActionID {
    IntroducePropertyAction,
    ExtractManagedDependenciesAction,
    RunBuildAction,
    ExecuteMavenRunConfigurationAction,
  }

  companion object {
    @JvmStatic
    fun trigger(project: Project?,
                actionID: ActionID,
                place: String?,
                isFromContextMenu: Boolean,
                executor : Executor? = null) {
      val data = FeatureUsageData().addOS().addProject(project)

      if (place != null) {
        data.addPlace(place).addData("context_menu", isFromContextMenu)
      }
      executor?.let { data.addExecutor(it) }

      FUCounterUsageLogger.getInstance().logEvent(GROUP_ID, actionID.name, data)
    }

    @JvmStatic
    fun trigger(project: Project?, actionID: ActionID, event: AnActionEvent?, executor : Executor? = null) {
      trigger(project, actionID, event?.place, event?.isFromContextMenu ?: false, executor)
    }

    @JvmStatic
    fun trigger(project: Project?, feature: ActionID) {
      if (project == null) return
      FUCounterUsageLogger.getInstance().logEvent(project, GROUP_ID, feature.name)
    }
  }
}
