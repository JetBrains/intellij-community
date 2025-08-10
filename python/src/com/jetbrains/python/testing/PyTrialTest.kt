// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing

import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonHelper

/**
 * trial test runner
 */

class PyTrialTestSettingsEditor(configuration: PyAbstractTestConfiguration) :
  PyAbstractTestSettingsEditor(PyTestSharedForm.create(configuration))

class PyTrialTestExecutionEnvironment(configuration: PyTrialTestConfiguration, environment: ExecutionEnvironment) :
  PyTestExecutionEnvironment<PyTrialTestConfiguration>(configuration, environment) {
  override fun getRunner(): PythonHelper = PythonHelper.TRIAL
}


class PyTrialTestConfiguration(project: Project, factory: PyTrialTestFactory)
  : PyAbstractTestConfiguration(project, factory) {


  override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
    PyTrialTestExecutionEnvironment(this, environment)

  override fun createConfigurationEditor(): SettingsEditor<PyAbstractTestConfiguration> =
    PyTrialTestSettingsEditor(this)

  override fun shouldSeparateTargetPath() = false
}


class PyTrialTestFactory(type:PythonTestConfigurationType) : PyAbstractTestFactory<PyTrialTestConfiguration>(type) {
  override fun createTemplateConfiguration(project: Project): PyTrialTestConfiguration = PyTrialTestConfiguration(project, this)

  override fun getName(): String = PyBundle.message("runcfg.trial.display_name")

  override fun getId(): String = "Twisted Trial"

  override fun onlyClassesAreSupported(project: Project, sdk: Sdk): Boolean = true

  override val packageRequired: String = "Twisted"
}
