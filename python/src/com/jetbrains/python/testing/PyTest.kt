// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing

import com.intellij.execution.Executor
import com.intellij.execution.Location
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.value.TargetEnvironmentFunction
import com.intellij.execution.target.value.constant
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.execution.ParametersListUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.run.targetBasedConfiguration.PyRunTargetVariant
import com.jetbrains.python.testing.PyTestSharedForm.create
import org.jetbrains.annotations.NotNull

/**
 * Pytest runner
 */

//Fetch param from parametrized test name spam[eggs]
private val PARAM_REGEX = Regex("\\[(.+)]$")

class PyTestSettingsEditor(configuration: PyAbstractTestConfiguration) :
  PyAbstractTestSettingsEditor(
    create(
      configuration,
      PyTestCustomOption(PyTestConfiguration::keywords, PyRunTargetVariant.PATH, PyRunTargetVariant.PYTHON),
      PyTestCustomOption(PyTestConfiguration::parameters, PyRunTargetVariant.PATH, PyRunTargetVariant.PYTHON),
    ))

class PyPyTestExecutionEnvironment(configuration: PyTestConfiguration, environment: ExecutionEnvironment) :
  PyTestExecutionEnvironment<PyTestConfiguration>(configuration, environment) {
  override fun getRunner(): PythonHelper = PythonHelper.PYTEST

  override fun customizeEnvironmentVars(envs: MutableMap<String, String>, passParentEnvs: Boolean) {
    super.customizeEnvironmentVars(envs, passParentEnvs)
    envs[PYTEST_RUN_CONFIG] = "True"
  }

  override fun customizePythonExecutionEnvironmentVars(targetEnvironmentRequest: @NotNull TargetEnvironmentRequest,
                                                       envs: @NotNull MutableMap<String, TargetEnvironmentFunction<String>>,
                                                       passParentEnvs: Boolean) {
    super.customizePythonExecutionEnvironmentVars(targetEnvironmentRequest, envs, passParentEnvs)
    envs[PYTEST_RUN_CONFIG] = constant("True")
  }
}


class PyTestConfiguration(project: Project, factory: PyTestFactory)
  : PyAbstractTestConfiguration(project, factory),
    PyTestConfigurationWithCustomSymbol {
  @ConfigField("runcfg.pytest.config.keywords")
  var keywords: String = ""

  @ConfigField("runcfg.pytest.config.parameters")
  var parameters: String = ""

  override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? =
    PyPyTestExecutionEnvironment(this, environment)

  override fun createConfigurationEditor(): SettingsEditor<PyAbstractTestConfiguration> =
    PyTestSettingsEditor(this)

  override fun getCustomRawArgumentsString(forRerun: Boolean): String = mutableListOf<String>().apply {
    if (keywords.isNotEmpty()) add("-k $keywords")
    if (AdvancedSettings.getBoolean("python.pytest.swapdiff")) add("--jb-swapdiff")
  }.joinToString(" ")

  override fun getTestSpecsForRerun(scope: GlobalSearchScope, locations: MutableList<Pair<Location<*>, AbstractTestProxy>>): List<String> =
    // py.test reruns tests by itself, so we only need to run same configuration and provide --last-failed
    target.generateArgumentsLine(this) +
    listOf(rawArgumentsSeparator, "--last-failed") +
    ParametersListUtil.parse(additionalArguments)
      .filter(String::isNotEmpty)

  override fun getTestSpec(): List<String> {
    // Parametrized test must add parameter to target.
    // So, foo.spam becomes foo.spam[param]
    if (parameters.isNotEmpty() && target.targetType == PyRunTargetVariant.PYTHON) {
      return super.getTestSpec().toMutableList().apply {
        this[size - 1] = last() + "[$parameters]"
      }
    }
    return super.getTestSpec()
  }

  override fun setMetaInfo(metaInfo: String) {
    // Metainfo contains test name along with params.
    parameters = getParamFromMetaInfo(metaInfo)
  }

  /**
   * Fetch params from test name
   */
  private fun getParamFromMetaInfo(metaInfo: String) = PARAM_REGEX.find(metaInfo)?.groupValues?.getOrNull(1) ?: ""

  override val fileSymbolSeparator get() = "::"
  override val symbolSymbolSeparator get() = "::"

  override fun isSameAsLocation(target: ConfigurationTarget, metainfo: String?): Boolean {
    return super.isSameAsLocation(target, metainfo) && getParamFromMetaInfo(metainfo ?: "") == parameters
  }
}

class PyTestFactory : PyAbstractTestFactory<PyTestConfiguration>() {
  companion object {
    const val id = "py.test"  //Do not rename: used as ID for run configurations
  }

  override fun createTemplateConfiguration(project: Project): PyTestConfiguration = PyTestConfiguration(project, this)

  override fun getId() = PyTestFactory.id

  override fun getName(): String = PyBundle.message("runcfg.pytest.display_name")

  override val onlyClassesSupported: Boolean = false

  override val packageRequired: String = "pytest"
}

private const val PYTEST_RUN_CONFIG: String = "PYTEST_RUN_CONFIG"
