// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.pycharm.community.ide.impl.settings.PythonMainConfigurable

class PyCharmProjectConfigurableStartupActivity : ProjectActivity {

  @Suppress("DialogTitleCapitalization")
  override suspend fun execute(project: Project) {
    for (ep in Configurable.PROJECT_CONFIGURABLE.getExtensions(project)) {
      when (ep.id) {
        "reference.idesettings.debugger.python" -> {
          ep.groupId = PythonMainConfigurable.ID
          ep.key = "configurable.PyDebuggerConfigurable.pycharm.display.name"
          ep.bundle="messages.PyBundle"
          ep.groupWeight = 60
        }
        "PyPlotsConfigurable" -> {
          ep.groupId = PythonMainConfigurable.ID
          ep.key = "configurable.plots.pycharm.display.name"
          ep.bundle="messages.PyBundle"
          ep.groupWeight = 40
        }
        "com.jetbrains.python.configuration.PyIntegratedToolsModulesConfigurable" -> {
          ep.groupId = PythonMainConfigurable.ID
          ep.key = "configurable.PyIntegratedToolsModulesConfigurable.pycharm.display.name"
          ep.bundle="messages.PyBundle"
          ep.groupWeight = 20
        }
      }
    }
  }
}