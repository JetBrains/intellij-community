// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.


package com.jetbrains.python.testing

import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.configurations.RuntimeConfigurationWarning
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.util.ProgramParametersUtil
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.StandardFileSystems
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyNames
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.psi.resolve.PackageAvailabilitySpec
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest
import com.jetbrains.python.run.targetBasedConfiguration.PyRunTargetVariant
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.function.Function

/**
 * unittest
 */

@ApiStatus.Internal
class PyUnitTestSettingsEditor(configuration: PyAbstractTestConfiguration) :
  PyAbstractTestSettingsEditor(
    PyTestSharedForm.create(configuration,
                            PyTestCustomOption(PyUnitTestConfiguration::pattern, PyRunTargetVariant.PATH),
                            PyTestCustomOption(PyUnitTestConfiguration::runnerScript, *PyRunTargetVariant.entries.toTypedArray(),
                                               helpKey = "runcfg.unittest.config.runnerScript.help")))

@ApiStatus.Internal
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

  override fun customizeEnvironmentVars(envs: MutableMap<String, String>, passParentEnvs: Boolean) {
    super.customizeEnvironmentVars(envs, passParentEnvs)
    configuration.getRunnerScriptPath()?.let { envs[PyUnitTestConfiguration.RUNNER_SCRIPT_ENV] = it.toString() }
  }

  override fun customizePythonExecutionEnvironmentVars(
    helpersAwareTargetRequest: HelpersAwareTargetEnvironmentRequest,
    envs: MutableMap<String, Function<TargetEnvironment, String>>,
    passParentEnvs: Boolean,
  ) {
    super.customizePythonExecutionEnvironmentVars(helpersAwareTargetRequest, envs, passParentEnvs)
    configuration.getRunnerScriptPath()?.let {
      envs[PyUnitTestConfiguration.RUNNER_SCRIPT_ENV] = getTargetPath(helpersAwareTargetRequest.targetEnvironmentRequest, it)
    }
  }
}


class PyUnitTestConfiguration(project: Project, factory: PyUnitTestFactory) :
  PyAbstractTestConfiguration(project, factory) { // Bare functions not supported in unittest: classes only
  @ConfigField("runcfg.unittest.config.pattern")
  var pattern: String? = null

  /**
   * The script that discovers and runs the tests instead of `python -m unittest`, for example Django's `tests/runtests.py`.
   * The script gets the tests to run as dotted labels relative to the working directory.
   * It must run the tests with `unittest.TextTestRunner`, which the helper replaces to report the results to the IDE.
   * A relative path is relative to the working directory.
   */
  @ConfigField("runcfg.unittest.config.runnerScript")
  @ApiStatus.Internal
  var runnerScript: String? = null

  override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
    PyUnitTestExecutionEnvironment(this, environment)

  override fun createConfigurationEditor(): SettingsEditor<PyAbstractTestConfiguration> =
    PyUnitTestSettingsEditor(this)

  override fun getCustomRawArgumentsString(forRerun: Boolean): String {
    // Pattern can only be used with folders ("all in folder" in legacy terms)
    if ((!pattern.isNullOrEmpty()) && target.targetType != PyRunTargetVariant.CUSTOM && runnerScript.isNullOrBlank()) {
      val path = StandardFileSystems.local().findFileByPath(target.target) ?: return ""
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

  /**
   * @return the [runnerScript] with the macros expanded and resolved against the working directory,
   * or null if the configuration runs the tests with `unittest`
   * @throws InvalidPathException if the [runnerScript] is not a valid path
   */
  @ApiStatus.Internal
  fun getRunnerScriptPath(): Path? {
    val script = getExpandedRunnerScript() ?: return null
    if (isSetupPyBased()) return null
    return Path.of(workingDirectorySafe).resolve(script)
  }

  private fun getExpandedRunnerScript(): String? {
    val script = runnerScript?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return ProgramParametersUtil.expandPathAndMacros(script, module, project)
  }

  /**
   * The labels of a runner script start at the directory of the script, as `runtests.py` and `manage.py` expect.
   * A relative script path does not give a directory.
   */
  override fun getWorkingDirectoryForContext(): String? {
    val script = getExpandedRunnerScript() ?: return null
    val scriptPath = try {
      Path.of(script)
    }
    catch (_: InvalidPathException) {
      return null
    }
    return if (scriptPath.isAbsolute) scriptPath.parent?.toString() else null
  }

  // setup.py runner is not id-based
  override fun isIdTestBased(): Boolean = !isSetupPyBased()

  override fun checkConfiguration() {
    super.checkConfiguration()
    if (target.targetType == PyRunTargetVariant.PATH && target.target.endsWith(".py") && !pattern.isNullOrEmpty()) {
      throw RuntimeConfigurationWarning(PyBundle.message("python.testing.pattern.can.only.be.used"))
    }
    val runnerScriptPath = try {
      getRunnerScriptPath()
    }
    catch (_: InvalidPathException) {
      throw RuntimeConfigurationError(PyBundle.message("python.testing.runner.script.invalid", runnerScript))
    } ?: return
    if (!Files.isRegularFile(runnerScriptPath)) {
      throw RuntimeConfigurationWarning(PyBundle.message("python.testing.runner.script.not.found", runnerScriptPath))
    }
    if (!pattern.isNullOrEmpty()) {
      throw RuntimeConfigurationWarning(PyBundle.message("python.testing.pattern.not.used.with.runner.script"))
    }
  }

  // Unittest does not support filesystem path. It needs qname resolvable against root or working directory
  override fun shouldSeparateTargetPath() = false

  @ApiStatus.Internal
  companion object {
    /**
     * The environment variable that gives [getRunnerScriptPath] to the unittest helper
     */
    @ApiStatus.Internal
    const val RUNNER_SCRIPT_ENV: String = "JB_UNITTEST_RUNNER_SCRIPT"
  }
}

class PyUnitTestFactory(type: PythonTestConfigurationType) : PyAbstractTestFactory<PyUnitTestConfiguration>(type) {
  companion object {
    const val id: String = "Unittests"
  }

  override fun createTemplateConfiguration(project: Project): PyUnitTestConfiguration = PyUnitTestConfiguration(project, this)

  override fun getName(): String = PyBundle.message("runcfg.unittest.display_name")

  override fun getId(): String = PyUnitTestFactory.id

  override fun onlyClassesAreSupported(project: Project, sdk: Sdk): Boolean = true

  override val packageSpec: PackageAvailabilitySpec? = null
}
