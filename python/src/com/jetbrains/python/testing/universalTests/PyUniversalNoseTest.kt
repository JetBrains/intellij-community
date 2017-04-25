/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.jetbrains.python.testing.universalTests

import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.testing.PythonTestConfigurationsModel
import com.jetbrains.python.testing.VFSTestFrameworkListener

/**
 * Nose runner
 */

class PyUniversalNoseTestSettingsEditor(configuration: PyUniversalTestConfiguration) :
  PyUniversalTestSettingsEditor(
    PyUniversalTestForm.create(configuration, PyUniversalTestForm.CustomOption(
      PyUniversalNoseTestConfiguration::regexPattern.name, TestTargetType.PATH)))

class PyUniversalNoseTestExecutionEnvironment(configuration: PyUniversalNoseTestConfiguration, environment: ExecutionEnvironment) :
  PyUniversalTestExecutionEnvironment<PyUniversalNoseTestConfiguration>(configuration, environment) {
  override fun getRunner() = PythonHelper.NOSE
}


class PyUniversalNoseTestConfiguration(project: Project, factory: PyUniversalNoseTestFactory) : PyUniversalTestConfiguration(project, factory) {
  @ConfigField
  var regexPattern = ""

  override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? =
    PyUniversalNoseTestExecutionEnvironment(this, environment)

  override fun createConfigurationEditor(): SettingsEditor<PyUniversalTestConfiguration> =
    PyUniversalNoseTestSettingsEditor(this)

  override fun getCustomRawArgumentsString(forRerun: Boolean): String =
    when {
      regexPattern.isEmpty() -> ""
      else -> "-m $regexPattern"
    }

  override fun isFrameworkInstalled() = VFSTestFrameworkListener.getInstance().isNoseTestInstalled(sdk)

}

object PyUniversalNoseTestFactory : PyUniversalTestFactory<PyUniversalNoseTestConfiguration>() {
  override fun createTemplateConfiguration(project: Project) = PyUniversalNoseTestConfiguration(project, this)

  override fun getName(): String = PythonTestConfigurationsModel.PYTHONS_NOSETEST_NAME
}