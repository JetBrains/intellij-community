// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.newProject.welcome

import com.intellij.internal.statistic.eventLog.EventFields
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

class PyWelcomeCollector : CounterUsagesCollector() {

  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    internal enum class ProjectType { NEW, OPENED }
    internal enum class ScriptResult { CREATED, NOT_EMPTY, NO_VFILE, NO_PSI, NO_DOCUMENT }
    internal enum class ProjectViewResult { EXPANDED, NO_TOOLWINDOW, NO_PANE, NO_TREE }

    internal fun logWelcomeProject(project: Project, projectType: ProjectType): Unit = welcomeProjectEvent.log(project, projectType)

    internal fun logWelcomeScript(project: Project, scriptResult: ScriptResult): Unit = welcomeScriptEvent.log(project, scriptResult)

    internal fun logWelcomeProjectView(project: Project, result: ProjectViewResult): Unit = welcomeProjectViewEvent.log(project, result)

    private val GROUP = EventLogGroup("python.welcome.events", 1)

    private val welcomeProjectEvent =
      GROUP.registerEvent("welcome.project", EventFields.Enum("project_type", ProjectType::class.java))

    private val welcomeScriptEvent =
      GROUP.registerEvent("welcome.script", EventFields.Enum("script_result", ScriptResult::class.java))

    private val welcomeProjectViewEvent =
      GROUP.registerEvent("welcome.projectView", EventFields.Enum("project_view_result", ProjectViewResult::class.java))
  }
}