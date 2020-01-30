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
import com.intellij.execution.Location
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyNames
import com.jetbrains.python.PythonHelper
import com.intellij.execution.target.value.TargetEnvironmentFunction
import com.intellij.execution.target.value.constant
import com.jetbrains.python.run.targetBasedConfiguration.PyRunTargetVariant
import com.jetbrains.python.testing.PyTestSharedForm.*
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
      CustomOption(
        PyTestConfiguration::keywords.name,
        PyBundle.message("python.testing.nose.custom.options.keywords"),
        PyRunTargetVariant.PATH,
        PyRunTargetVariant.PYTHON
      ),
      CustomOption(
        PyTestConfiguration::parameters.name,
        PyBundle.message("python.testing.nose.custom.options.parameters"),
        PyRunTargetVariant.PATH,
        PyRunTargetVariant.PYTHON
      )
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
  : PyAbstractTestConfiguration(project, factory, PyTestFrameworkService.getSdkReadableNameByFramework(PyNames.PY_TEST)),
    PyTestConfigurationWithCustomSymbol {
  @ConfigField
  var keywords: String = ""

  @ConfigField
  var parameters: String = ""

  override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? =
    PyPyTestExecutionEnvironment(this, environment)

  override fun createConfigurationEditor(): SettingsEditor<PyAbstractTestConfiguration> =
    PyTestSettingsEditor(this)

  override fun getCustomRawArgumentsString(forRerun: Boolean): String =
    when {
      keywords.isEmpty() -> ""
      else -> "-k $keywords"
    }

  override fun getTestSpecsForRerun(scope: GlobalSearchScope, locations: MutableList<Pair<Location<*>, AbstractTestProxy>>): List<String> {
    // py.test reruns tests by itself, so we only need to run same configuration and provide --last-failed
    return target.generateArgumentsLine(this) + listOf(rawArgumentsSeparator, "--last-failed")
  }

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

  override fun isFrameworkInstalled(): Boolean = VFSTestFrameworkListener.getInstance().isTestFrameworkInstalled(sdk, PyNames.PY_TEST)

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
  override fun createTemplateConfiguration(project: Project): PyTestConfiguration = PyTestConfiguration(project, this)

  override fun getName(): String = PyTestFrameworkService.getSdkReadableNameByFramework(PyNames.PY_TEST)

  override fun getId() = "py.test" //Do not rename: used as ID for run configurations
}

private const val PYTEST_RUN_CONFIG: String = "PYTEST_RUN_CONFIG"
