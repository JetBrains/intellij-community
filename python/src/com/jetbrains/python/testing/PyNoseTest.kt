// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
 * Nose runner
 */

class PyNoseTestSettingsEditor(configuration: PyAbstractTestConfiguration) :
  PyAbstractTestSettingsEditor(
    PyTestSharedForm.create(configuration, PyTestSharedForm.CustomOption(
      PyNoseTestConfiguration::regexPattern.name, PyRunTargetVariant.PATH)))

class PyNoseTestExecutionEnvironment(configuration: PyNoseTestConfiguration, environment: ExecutionEnvironment) :
  PyTestExecutionEnvironment<PyNoseTestConfiguration>(configuration, environment) {
  override fun getRunner(): PythonHelper = PythonHelper.NOSE
}


class PyNoseTestConfiguration(project: Project, factory: PyNoseTestFactory) :
  PyAbstractTestConfiguration(project, factory, PyTestFrameworkService.getSdkReadableNameByFramework(PyNames.NOSE_TEST)) {
  @ConfigField
  var regexPattern: String = ""

  override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? =
    PyNoseTestExecutionEnvironment(this, environment)

  override fun createConfigurationEditor(): SettingsEditor<PyAbstractTestConfiguration> =
    PyNoseTestSettingsEditor(this)

  override fun getCustomRawArgumentsString(forRerun: Boolean): String =
    when {
      regexPattern.isEmpty() -> ""
      else -> "-m $regexPattern"
    }

  override fun isFrameworkInstalled(): Boolean = VFSTestFrameworkListener.getInstance().isTestFrameworkInstalled(sdk, PyNames.NOSE_TEST)
}

object PyNoseTestFactory : PyAbstractTestFactory<PyNoseTestConfiguration>() {
  override fun createTemplateConfiguration(project: Project) = PyNoseTestConfiguration(project, this)

  override fun getName(): String = PyTestFrameworkService.getSdkReadableNameByFramework(PyNames.NOSE_TEST)
}