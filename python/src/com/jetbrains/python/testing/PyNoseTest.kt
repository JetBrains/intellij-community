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
import com.jetbrains.python.run.targetBasedConfiguration.PyRunTargetVariant

/**
 * Nose runner
 */

class PyNoseTestSettingsEditor(configuration: PyAbstractTestConfiguration) :
  PyAbstractTestSettingsEditor(
    PyTestSharedForm.create(configuration, PyTestCustomOption(
      PyNoseTestConfiguration::regexPattern, PyRunTargetVariant.PATH)))

class PyNoseTestExecutionEnvironment(configuration: PyNoseTestConfiguration, environment: ExecutionEnvironment) :
  PyTestExecutionEnvironment<PyNoseTestConfiguration>(configuration, environment) {
  override fun getRunner(): PythonHelper = PythonHelper.NOSE
}


class PyNoseTestConfiguration(project: Project, factory: PyNoseTestFactory) :
  PyAbstractTestConfiguration(project, factory),
  PyTestConfigurationWithCustomSymbol {
  @ConfigField("runcfg.nosetests.config.regexPattern")
  var regexPattern: String = ""

  override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
    PyNoseTestExecutionEnvironment(this, environment)

  override fun createConfigurationEditor(): SettingsEditor<PyAbstractTestConfiguration> =
    PyNoseTestSettingsEditor(this)

  override fun getCustomRawArgumentsString(forRerun: Boolean): String =
    when {
      regexPattern.isEmpty() -> ""
      else -> "-m $regexPattern"
    }

  override val fileSymbolSeparator get() = ":"
  override val symbolSymbolSeparator get() = "."
}

class PyNoseTestFactory(type: PythonTestConfigurationType) : PyAbstractTestFactory<PyNoseTestConfiguration>(type) {
  companion object {
    const val id = "Nosetests"
  }

  override fun createTemplateConfiguration(project: Project) = PyNoseTestConfiguration(project, this)

  override fun getId(): String = PyNoseTestFactory.id

  override fun getName(): String = PyBundle.message("runcfg.nosetests.display_name")

  override fun onlyClassesAreSupported(project: Project, sdk: Sdk): Boolean = false

  override val packageRequired: String = "nose"
}
