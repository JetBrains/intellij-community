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
import com.jetbrains.python.run.targetBasedConfiguration.PyRunTargetVariant

/**
 * Py.test runner
 */

class PyTestSettingsEditor(configuration: PyAbstractTestConfiguration) :
  PyAbstractTestSettingsEditor(
    PyTestSharedForm.create(configuration, PyTestSharedForm.CustomOption(
      PyTestConfiguration::keywords.name, PyRunTargetVariant.PATH, PyRunTargetVariant.PYTHON)))

class PyPyTestExecutionEnvironment(configuration: PyTestConfiguration, environment: ExecutionEnvironment) :
  PyTestExecutionEnvironment<PyTestConfiguration>(configuration, environment) {
  override fun getRunner(): PythonHelper = PythonHelper.PYTEST
}


class PyTestConfiguration(project: Project, factory: PyTestFactory)
  : PyAbstractTestConfiguration(project, factory, PyTestFrameworkService.getSdkReadableNameByFramework(PyNames.PY_TEST)) {
  @ConfigField
  var keywords: String = ""

  override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? =
    PyPyTestExecutionEnvironment(this, environment)

  override fun createConfigurationEditor(): SettingsEditor<PyAbstractTestConfiguration> =
    PyTestSettingsEditor(this)

  override fun getCustomRawArgumentsString(forRerun: Boolean): String =
    when {
      keywords.isEmpty() -> ""
      else -> "-k $keywords"
    } + if (forRerun) " --last-failed" else ""

  override fun isFrameworkInstalled(): Boolean = VFSTestFrameworkListener.getInstance().isTestFrameworkInstalled(sdk, PyNames.PY_TEST)

  override fun setMetaInfo(metaInfo: String) {
    keywords = metaInfo
  }

  override fun isSameAsLocation(target: ConfigurationTarget, metainfo: String?): Boolean {
    return super.isSameAsLocation(target, metainfo) && metainfo == keywords
  }
}

object PyTestFactory : PyAbstractTestFactory<PyTestConfiguration>() {
  override fun createTemplateConfiguration(project: Project): PyTestConfiguration = PyTestConfiguration(project, this)

  override fun getName(): String = PyTestFrameworkService.getSdkReadableNameByFramework(PyNames.PY_TEST)
}