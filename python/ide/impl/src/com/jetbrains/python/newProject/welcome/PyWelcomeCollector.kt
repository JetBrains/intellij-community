// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.newProject.welcome

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class PyWelcomeCollector : CounterUsagesCollector() {

  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    private val GROUP = EventLogGroup("python.welcome.events", 1)
    private val welcomeScriptForNewProjectEvent = GROUP.registerEvent("welcome_script_new_project")
    private val welcomeScriptForOpenedProjectEvent = GROUP.registerEvent("welcome_script_opened_project")

    fun logWelcomeScriptForNewProject(): Unit = welcomeScriptForNewProjectEvent.log()

    fun logWelcomeScriptForOpenedProject(): Unit = welcomeScriptForOpenedProjectEvent.log()
  }
}