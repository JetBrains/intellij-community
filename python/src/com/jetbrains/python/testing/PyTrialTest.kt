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
 * Py.test runner
 */

class PyTrialTestSettingsEditor(configuration: PyAbstractTestConfiguration) :
  PyAbstractTestSettingsEditor(PyTestSharedForm.create(configuration))

class PyTrialTestExecutionEnvironment(configuration: PyTrialTestConfiguration, environment: ExecutionEnvironment) :
  PyTestExecutionEnvironment<PyTrialTestConfiguration>(configuration, environment) {
  override fun getRunner() = PythonHelper.TRIAL
}


class PyTrialTestConfiguration(project: Project, factory: PyTrialTestFactory)
  : PyAbstractTestConfiguration(project, factory, PyTestFrameworkService.getSdkReadableNameByFramework(PyNames.TRIAL_TEST)) {


  override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? =
    PyTrialTestExecutionEnvironment(this, environment)

  override fun createConfigurationEditor(): SettingsEditor<PyAbstractTestConfiguration> =
    PyTrialTestSettingsEditor(this)

  override fun shouldSeparateTargetPath() = false

  override fun isFrameworkInstalled() = VFSTestFrameworkListener.getInstance().isTestFrameworkInstalled(sdk, PyNames.TRIAL_TEST)

}


object PyTrialTestFactory : PyAbstractTestFactory<PyTrialTestConfiguration>() {
  override fun createTemplateConfiguration(project: Project) = PyTrialTestConfiguration(project, this)

  override fun getName(): String = PyTestFrameworkService.getSdkReadableNameByFramework(PyNames.TRIAL_TEST)
}