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


package com.jetbrains.python.testing

import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.jetbrains.python.PyNames
import com.jetbrains.python.PythonHelper

/**
 * Nose runner
 */

class PyNoseTestSettingsEditor(configuration: PyAbstractTestConfiguration) :
  PyAbstractTestSettingsEditor(
    PyTestSharedForm.create(configuration, PyTestSharedForm.CustomOption(
      PyNoseTestConfiguration::regexPattern.name, TestTargetType.PATH)))

class PyNoseTestExecutionEnvironment(configuration: PyNoseTestConfiguration, environment: ExecutionEnvironment) :
  PyTestExecutionEnvironment<PyNoseTestConfiguration>(configuration, environment) {
  override fun getRunner() = PythonHelper.NOSE
}


class PyNoseTestConfiguration(project: Project, factory: PyNoseTestFactory) :
  PyAbstractTestConfiguration(project, factory, PyTestFrameworkService.getSdkReadableNameByFramework(PyNames.NOSE_TEST)) {
  @ConfigField
  var regexPattern = ""

  override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? =
    PyNoseTestExecutionEnvironment(this, environment)

  override fun createConfigurationEditor(): SettingsEditor<PyAbstractTestConfiguration> =
    PyNoseTestSettingsEditor(this)

  override fun getCustomRawArgumentsString(forRerun: Boolean): String =
    when {
      regexPattern.isEmpty() -> ""
      else -> "-m $regexPattern"
    }

  override fun isFrameworkInstalled() = VFSTestFrameworkListener.getInstance().isTestFrameworkInstalled(sdk, PyNames.NOSE_TEST)

}

object PyNoseTestFactory : PyAbstractTestFactory<PyNoseTestConfiguration>() {
  override fun createTemplateConfiguration(project: Project) = PyNoseTestConfiguration(project, this)

  override fun getName(): String = PyTestFrameworkService.getSdkReadableNameByFramework(PyNames.NOSE_TEST)
}