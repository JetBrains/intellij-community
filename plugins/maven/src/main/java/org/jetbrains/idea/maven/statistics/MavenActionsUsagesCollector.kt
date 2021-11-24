// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.statistics

import com.intellij.execution.Executor
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

class MavenActionsUsagesCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup {
    return GROUP
  }

  companion object {
    private val GROUP = EventLogGroup("build.maven.actions", 3)
    private val EXECUTOR_FIELD = EventFields.StringValidatedByCustomRule("executor", "run_config_executor")
    private val CONTEXT_MENU = EventFields.Boolean("context_menu")

    @JvmField
    val RUN_BUILD_ACTION = GROUP.registerVarargEvent("RunBuildAction",
                                                     EventFields.ActionPlace,
                                                     CONTEXT_MENU,
                                                     EXECUTOR_FIELD,
                                                     EventFields.PluginInfo)

    @JvmField
    val EXECUTE_MAVEN_CONFIGURATION = GROUP.registerVarargEvent("ExecuteMavenRunConfigurationAction",
                                                                EventFields.ActionPlace,
                                                                CONTEXT_MENU)

    @JvmField
    val INTRODUCE_PROPERTY = GROUP.registerEvent("IntroducePropertyAction")

    @JvmField
    val EXTRACT_MANAGED_DEPENDENCIES = GROUP.registerEvent("ExtractManagedDependenciesAction")

    @JvmField
    val SHOW_MAVEN_CONNECTORS = GROUP.registerEvent("ShowMavenConnectors")

    @JvmField
    val KILL_MAVEN_CONNECTOR = GROUP.registerEvent("KillMavenConnector")

    @JvmField
    val START_LOCAL_MAVEN_SERVER = GROUP.registerEvent("StartLocalMavenServer")

    @JvmField
    val START_WSL_MAVEN_SERVER = GROUP.registerEvent("StartWslMavenServer")

    @JvmField
    val CREATE_MAVEN_PROJECT = GROUP.registerEvent("CreateMavenProjectOrModule")

    @JvmField
    val CREATE_MAVEN_PROJECT_FROM_ARCHETYPE = GROUP.registerEvent("CreateMavenProjectOrModuleFromArchetype")

    @JvmStatic
    fun trigger(project: Project?,
                actionID: VarargEventId,
                place: String?,
                isFromContextMenu: Boolean,
                executor: Executor? = null) {
      val data = mutableListOf<EventPair<*>>()

      if (place != null) {
        data.add(EventFields.ActionPlace.with(place))
        data.add(CONTEXT_MENU.with(isFromContextMenu))
      }

      if (executor != null) {
        EXECUTOR_FIELD.with(executor.id)
      }

      actionID.log(project, data)
    }

    @JvmStatic
    fun trigger(project: Project?, feature: EventId) {
      if (project == null) return
      feature.log(project)
    }
  }
}
