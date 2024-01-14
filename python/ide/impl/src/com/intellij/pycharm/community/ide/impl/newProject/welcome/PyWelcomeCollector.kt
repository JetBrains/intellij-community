// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.newProject.welcome

import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

class PyWelcomeCollector : CounterUsagesCollector() {

  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    internal enum class ProjectType { NEW, OPENED }
    internal enum class ScriptResult { DISABLED_BUT_COULD, DISABLED_AND_COULD_NOT, CREATED, NOT_EMPTY, NO_VFILE, NO_PSI, NO_DOCUMENT }
    internal enum class ProjectViewPoint { IMMEDIATELY, FROM_LISTENER }
    internal enum class ProjectViewResult { EXPANDED, REJECTED, NO_TOOLWINDOW }
    internal enum class RunConfigurationResult { CREATED, NULL }

    internal fun logWelcomeProject(project: Project, type: ProjectType): Unit = welcomeProjectEvent.log(project, type)

    internal fun logWelcomeScript(project: Project, result: ScriptResult): Unit = welcomeScriptEvent.log(project, result)

    internal fun logWelcomeProjectView(project: Project, point: ProjectViewPoint, result: ProjectViewResult) {
      welcomeProjectViewEvent.log(project, point, result)
    }

    internal fun logWelcomeRunConfiguration(project: Project, result: RunConfigurationResult) {
      welcomeRunConfigurationEvent.log(project, result)
    }

    private val GROUP = EventLogGroup("python.welcome.events", 2)

    private val welcomeProjectEvent =
      GROUP.registerEvent("welcome.project", EventFields.Enum("project_type", ProjectType::class.java))

    private val welcomeScriptEvent =
      GROUP.registerEvent("welcome.script", EventFields.Enum("script_result", ScriptResult::class.java))

    private val welcomeProjectViewEvent =
      GROUP.registerEvent("welcome.projectView",
                          EventFields.Enum("project_view_point", ProjectViewPoint::class.java),
                          EventFields.Enum("project_view_result", ProjectViewResult::class.java))

    private val welcomeRunConfigurationEvent =
      GROUP.registerEvent("welcome.runConfiguration", EventFields.Enum("run_configuration_result", RunConfigurationResult::class.java))
  }
}