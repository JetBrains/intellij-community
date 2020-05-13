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
import com.intellij.execution.configurations.RuntimeConfigurationWarning
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyNames
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.run.targetBasedConfiguration.PyRunTargetVariant

/**
 * unittest
 */

class PyUnitTestSettingsEditor(configuration: PyAbstractTestConfiguration) :
  PyAbstractTestSettingsEditor(
    PyTestSharedForm.create(configuration,
                            PyTestSharedForm.CustomOption(PyUnitTestConfiguration::pattern.name, PyRunTargetVariant.PATH)
    ))

class PyUnitTestExecutionEnvironment(configuration: PyUnitTestConfiguration, environment: ExecutionEnvironment) :
  PyTestExecutionEnvironment<PyUnitTestConfiguration>(configuration, environment) {

  override fun getRunner(): PythonHelper =
    // different runner is used for setup.py
    if (configuration.isSetupPyBased()) {
      PythonHelper.SETUPPY
    }
    else {
      PythonHelper.UNITTEST
    }

}


class PyUnitTestConfiguration(project: Project, factory: PyUnitTestFactory) :
  PyAbstractTestConfiguration(project, factory,
                              PythonTestConfigurationsModel.getPythonsUnittestName()) { // Bare functions not supported in unittest: classes only
  @ConfigField
  var pattern: String? = null

  override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? =
    PyUnitTestExecutionEnvironment(this, environment)

  override fun createConfigurationEditor(): SettingsEditor<PyAbstractTestConfiguration> =
    PyUnitTestSettingsEditor(this)

  override fun getCustomRawArgumentsString(forRerun: Boolean): String {
    // Pattern can only be used with folders ("all in folder" in legacy terms)
    if ((!pattern.isNullOrEmpty()) && target.targetType != PyRunTargetVariant.CUSTOM) {
      val path = LocalFileSystem.getInstance().findFileByPath(target.target) ?: return ""
      // "Pattern" works only for "discovery" mode and for "rerun" we are using "python" targets ("concrete" tests)
      return if (path.isDirectory && !forRerun) "-p $pattern" else ""
    }
    else {
      return ""
    }

  }

  /**
   * @return configuration should use runner for setup.py
   */
  internal fun isSetupPyBased(): Boolean {
    val setupPy = target.targetType == PyRunTargetVariant.PATH && target.target.endsWith(PyNames.SETUP_DOT_PY)
    return setupPy
  }

  // setup.py runner is not id-based
  override fun isIdTestBased(): Boolean = !isSetupPyBased()

  override fun checkConfiguration() {
    super.checkConfiguration()
    if (target.targetType == PyRunTargetVariant.PATH && target.target.endsWith(".py") && !pattern.isNullOrEmpty()) {
      throw RuntimeConfigurationWarning(PyBundle.message("python.testing.pattern.can.only.be.used"))
    }
  }

  override fun isFrameworkInstalled(): Boolean = true //Unittest is always available

  // Unittest does not support filesystem path. It needs qname resolvable against root or working directory
  override fun shouldSeparateTargetPath() = false
}

class PyUnitTestFactory : PyAbstractTestFactory<PyUnitTestConfiguration>() {
  override fun createTemplateConfiguration(project: Project): PyUnitTestConfiguration = PyUnitTestConfiguration(project, this)

  override fun getName(): String = PythonTestConfigurationsModel.getPythonsUnittestName()

  override fun getId(): String = "Unittests"
}
