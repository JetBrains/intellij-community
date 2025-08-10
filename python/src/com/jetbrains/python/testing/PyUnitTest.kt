// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.


package com.jetbrains.python.testing

import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationWarning
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
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
                            PyTestCustomOption(PyUnitTestConfiguration::pattern, PyRunTargetVariant.PATH)))

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
  PyAbstractTestConfiguration(project, factory) { // Bare functions not supported in unittest: classes only
  @ConfigField("runcfg.unittest.config.pattern")
  var pattern: String? = null

  override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
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

  // Unittest does not support filesystem path. It needs qname resolvable against root or working directory
  override fun shouldSeparateTargetPath() = false
}

class PyUnitTestFactory(type: PythonTestConfigurationType) : PyAbstractTestFactory<PyUnitTestConfiguration>(type) {
  companion object {
    const val id: String = "Unittests"
  }

  override fun createTemplateConfiguration(project: Project): PyUnitTestConfiguration = PyUnitTestConfiguration(project, this)

  override fun getName(): String = PyBundle.message("runcfg.unittest.display_name")

  override fun getId(): String = PyUnitTestFactory.id

  override fun onlyClassesAreSupported(project: Project, sdk: Sdk): Boolean = true
}
