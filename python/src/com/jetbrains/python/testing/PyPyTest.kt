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
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.testing.PythonTestConfigurationsModel
import com.jetbrains.python.testing.VFSTestFrameworkListener

/**
 * Py.test runner
 */

class PyPyTestSettingsEditor(configuration: PyAbstractTestConfiguration) :
  PyAbstractTestSettingsEditor(
    PyTestSharedForm.create(configuration, PyTestSharedForm.CustomOption(
      PyPyTestConfiguration::keywords.name, TestTargetType.PATH, TestTargetType.PYTHON)))

class PyPyTestExecutionEnvironment(configuration: PyPyTestConfiguration, environment: ExecutionEnvironment) :
  PyTestExecutionEnvironment<PyPyTestConfiguration>(configuration, environment) {
  override fun getRunner() = PythonHelper.PYTEST
}


class PyPyTestConfiguration(project: Project, factory: PyPyTestFactory) : PyAbstractTestConfiguration(project, factory) {
  @ConfigField
  var keywords = ""

  override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? =
    PyPyTestExecutionEnvironment(this, environment)

  override fun createConfigurationEditor(): SettingsEditor<PyAbstractTestConfiguration> =
    PyPyTestSettingsEditor(this)

  override fun getCustomRawArgumentsString(forRerun: Boolean): String =
    when {
      keywords.isEmpty() -> ""
      else -> "-k $keywords"
    }

  override fun isFrameworkInstalled() = VFSTestFrameworkListener.getInstance().isPyTestInstalled(sdk)

}

object PyPyTestFactory : PyAbstractTestFactory<PyPyTestConfiguration>() {
  override fun createTemplateConfiguration(project: Project) = PyPyTestConfiguration(project, this)

  override fun getName(): String = PythonTestConfigurationsModel.PY_TEST_NAME
}