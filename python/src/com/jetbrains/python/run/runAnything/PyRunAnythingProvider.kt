// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run.runAnything

import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.ChooseRunConfigurationPopup
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ParametersList
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.ide.actions.runAnything.activity.RunAnythingMatchedRunConfigurationProvider
import com.intellij.openapi.actionSystem.DataContext
import com.jetbrains.python.run.PythonConfigurationType
import com.jetbrains.python.run.PythonRunConfiguration

/**
 * @author vlan
 */
class PyRunAnythingProvider : RunAnythingMatchedRunConfigurationProvider() {
  override fun createConfiguration(dataContext: DataContext, pattern: String): RunnerAndConfigurationSettings {
    val runManager = RunManager.getInstance(dataContext.project)
    val settings = runManager.createConfiguration(pattern, configurationFactory)
    val commandLine = ParametersList.parse(pattern)
    val arguments = commandLine.drop(1)
    val configuration = settings.configuration as? PythonRunConfiguration
    val workingDir = dataContext.virtualFile
    configuration?.apply {
      val first = arguments.getOrNull(0)
      when {
        first == "-m" -> {
          scriptName = arguments.getOrNull(1)
          scriptParameters = ParametersList.join(arguments.drop(2))
          isModuleMode = true
        }
        first?.startsWith("-m") == true -> {
          scriptName = first.substring(2)
          scriptParameters = ParametersList.join(arguments.drop(1))
          isModuleMode = true
        }
        else -> {
          scriptName = first
          scriptParameters = ParametersList.join(arguments.drop(1))
        }
      }
      workingDir?.findPythonSdk(project)?.let {
        sdkHome = it.homePath
      }
      workingDir?.let {
        workingDirectory = it.canonicalPath
      }
    }
    return settings
  }

  override fun getConfigurationFactory(): ConfigurationFactory =
    PythonConfigurationType.getInstance().factory

  override fun findMatchingValue(dataContext: DataContext, pattern: String): ChooseRunConfigurationPopup.ItemWrapper<*>? {
    if (!pattern.startsWith("python ")) return null
    val configuration = createConfiguration(dataContext, pattern)
    try {
      configuration.checkSettings()
    }
    catch (e: RuntimeConfigurationException) {
      return null
    }
    return ChooseRunConfigurationPopup.ItemWrapper.wrap(dataContext.project, configuration)
  }

  override fun getHelpCommand() = "python"

  override fun getHelpGroupTitle(): String? = "Python"  // NON-NLS

  override fun getHelpCommandPlaceholder() = "python <file name>"
}
