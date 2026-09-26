// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.customization.frontend

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/*
 Similar setup but form applicationConfigurable we have in
 [com.intellij.pycharm.community.ide.impl.PyCharmCorePluginConfigurator]

 ProjectActivity.execute executes after the project is fully loaded, so when we open settings too early, we will see

 Full list of settings in Pycharm group with weights and configurables ids:

 Interpreter, weight=200 (set in XML) / projectConfigurable "com.jetbrains.python.configuration.PyActiveSdkModuleConfigurable"
 TypeEngine, weight=190 (set in XML) / projectConfigurable "com.intellij.python.lsp.core.PyTypeEngineConfigurable"
 Debugger, weight=80 (set here), width=119 (set in XML) / projectConfigurable "reference.idesettings.debugger.python"
   - TypeRenderers / applicationConfigurable "debugger.dataViews.python.type.renderers"
 Console weight=75 / projectConfigurable "pyconsole"
   - Flask Console
   - Python Console
 Tools weight=72 / groupConfigurable "python.tools.group.settings"
   - Pyright
   - Ruff
   - ty
   - Black
   - Integrated Tools
 Tables, width=70 (set in PyCharmCorePluginConfigurator) / applicationConfigurable "DSTables"
 Python Plots weight=60 (set in PyCharmCorePluginConfigurator) / applicationConfigurable "PyPlotsConfigurable"
 Django, weight=50 (set here) / projectConfigurable "com.intellij.python.django.customization.DjangoModulesConfigurable"
 Flask, weight=40 (set here) / projectConfigurable "com.intellij.python.pro.flask.configuration.FlaskConfigurable"
 External Documentation (set in PyCharmCorePluginConfigurator) / applicationConfigurable "com.jetbrains.python.documentation.PythonDocumentationConfigurable"
*/
internal class PyCharmProjectConfigurableStartupActivity : ProjectActivity {

  // Only for projectConfigurable.
  // applicationConfigurable is set up in [PyCharmCorePluginConfigurator].
  override suspend fun execute(project: Project) {
    for (ep in Configurable.PROJECT_CONFIGURABLE.getExtensions(project)) {
      when (ep.id) {
        // Moving to 'Python' section.
        "reference.idesettings.debugger.python" -> {
          ep.groupId = PYTHON_GROUP_ID
          @Suppress("DialogTitleCapitalization")
          ep.key = "configurable.PyDebuggerConfigurable.pycharm.display.name"
          ep.bundle = "messages.PyBundle"
          ep.groupWeight = 80
        }
        "pyconsole" -> {
          ep.groupId = PYTHON_GROUP_ID
          ep.groupWeight = 75
        }
        "com.intellij.python.django.customization.DjangoModulesConfigurable" -> {
          ep.groupId = PYTHON_GROUP_ID
          ep.groupWeight = 50
        }
        "com.intellij.python.pro.flask.configuration.FlaskConfigurable" -> {
          ep.groupId = PYTHON_GROUP_ID
          ep.groupWeight = 40
        }

        // Moving to 'Python | Tools' section.
        "com.jetbrains.python.configuration.PyIntegratedToolsModulesConfigurable" -> {
          ep.groupId = PYTHON_TOOLS_GROUP_ID
          @Suppress("DialogTitleCapitalization")
          ep.key = "configurable.PyIntegratedToolsModulesConfigurable.pycharm.display.name"
          ep.bundle = "messages.PyBundle"
          ep.groupWeight = 20
        }
        "com.intellij.python.ty.TyConfigurable" -> {
          ep.groupId = PYTHON_TOOLS_GROUP_ID
          ep.groupWeight = 40
        }
        "com.intellij.python.ruff.RuffConfigurable" -> {
          ep.groupId = PYTHON_TOOLS_GROUP_ID
          ep.groupWeight = 40
        }
        "com.intellij.python.pyright.PyrightConfigurable" -> {
          ep.groupId = PYTHON_TOOLS_GROUP_ID
          ep.groupWeight = 40
        }
        "com.jetbrains.python.black.configuration.BlackFormatterConfigurable" -> {
          ep.groupId = PYTHON_TOOLS_GROUP_ID
          ep.groupWeight = 30
        }
      }
    }
  }
}

private const val PYTHON_GROUP_ID: String = "python.group.settings"
private const val PYTHON_TOOLS_GROUP_ID: String = "python.tools.group.settings"
