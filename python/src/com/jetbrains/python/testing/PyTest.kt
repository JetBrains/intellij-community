// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing

import com.intellij.execution.Executor
import com.intellij.execution.Location
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.value.TargetEnvironmentFunction
import com.intellij.execution.target.value.constant
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.execution.ParametersListUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest
import com.jetbrains.python.run.targetBasedConfiguration.PyRunTargetVariant
import com.jetbrains.python.testing.PyTestSharedForm.create
import org.jetbrains.annotations.ApiStatus

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

  override fun customizePythonExecutionEnvironmentVars(helpersAwareTargetRequest: HelpersAwareTargetEnvironmentRequest,
                                                       envs: MutableMap<String, TargetEnvironmentFunction<String>>,
                                                       passParentEnvs: Boolean) {
    super.customizePythonExecutionEnvironmentVars(helpersAwareTargetRequest, envs, passParentEnvs)
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

  override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
    PyPyTestExecutionEnvironment(this, environment)

  override fun createConfigurationEditor(): SettingsEditor<PyAbstractTestConfiguration> {
    return if (Registry.`is`("pytest.new.run.config", false)) {
      PyAbstractTestConfigurationFragmentedEditor(this)
    } else {
      PyTestSettingsEditor(this)
    }
  }

  override fun isNewUiSupported(): Boolean = true

  override fun getCustomRawArgumentsString(forRerun: Boolean): String = mutableListOf<String>().apply {
    if (keywords.isNotEmpty() && target.targetType != PyRunTargetVariant.CUSTOM) {
      val keywords = keywords.removeSurrounding("'").removeSurrounding("\"")
      add("-k '$keywords'")
    }
    if (AdvancedSettings.getBoolean("python.pytest.swapdiff")) add("--jb-swapdiff")
    if (AdvancedSettings.getBoolean("python.pytest.show_summary")) add("--jb-show-summary")
  }.joinToString(" ")

  /**
   * *To be deprecated. The part of the legacy implementation based on [GeneralCommandLine].*
   */
  override fun getTestSpecsForRerun(scope: GlobalSearchScope, locations: List<Pair<Location<*>, AbstractTestProxy>>): List<String> =
    // py.test reruns tests by itself, so we only need to run same configuration and provide --last-failed
    target.generateArgumentsLine(this) +
    listOf(rawArgumentsSeparator, "--last-failed") +
    ParametersListUtil.parse(additionalArguments)
      .filter(String::isNotEmpty)

  override fun getTestSpecsForRerun(request: TargetEnvironmentRequest,
                                    scope: GlobalSearchScope,
                                    locations: List<Pair<Location<*>, AbstractTestProxy>>): List<TargetEnvironmentFunction<String>> =
    // py.test reruns tests by itself, so we only need to run same configuration and provide --last-failed
    target.generateArgumentsLine(request, this) +
    listOf(rawArgumentsSeparator, "--last-failed").map(::constant) +
    ParametersListUtil.parse(additionalArguments)
      .filter(String::isNotEmpty)
      .map(::constant)

  override val pythonTargetAdditionalParams: String
    get() =
      if (parameters.isNotEmpty() && target.targetType == PyRunTargetVariant.PYTHON) "[$parameters]" else ""

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

class PyTestFactory(type: PythonTestConfigurationType) : PyAbstractTestFactory<PyTestConfiguration>(type) {
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Obtain instance from PythonTestConfigurationType")
  constructor() : this(PythonTestConfigurationType.getInstance())

  companion object {
    const val id = "py.test"  //Do not rename: used as ID for run configurations
  }

  override fun createTemplateConfiguration(project: Project): PyTestConfiguration = PyTestConfiguration(project, this)

  override fun getId() = PyTestFactory.id

  override fun getName(): String = PyBundle.message("runcfg.pytest.display_name")

  override fun onlyClassesAreSupported(project: Project, sdk: Sdk): Boolean = false

  override val packageRequired: String = "pytest"
}

private const val PYTEST_RUN_CONFIG: String = "PYTEST_RUN_CONFIG"
