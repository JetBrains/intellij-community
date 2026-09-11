// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console

import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.value.TargetEnvironmentFunction
import com.intellij.execution.target.value.constant
import com.intellij.execution.target.value.getRelativeTargetPath
import com.intellij.execution.target.value.getTargetEnvironmentValueForLocalPath
import com.intellij.execution.target.value.joinToStringFunction
import com.intellij.execution.target.value.targetPath
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.PathMapper
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.SystemProperties
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.targetEnvironmentRequest
import com.jetbrains.python.console.PyConsoleOptions.PyConsoleSettings
import com.jetbrains.python.debugger.PyDebuggerOptionsProvider
import com.jetbrains.python.remote.PyRemotePathMapper
import com.jetbrains.python.run.EnvironmentController
import com.jetbrains.python.run.PlainEnvironmentController
import com.jetbrains.python.run.PythonRunConfiguration
import com.jetbrains.python.run.collectPythonPath
import com.jetbrains.python.run.toStringLiteral
import com.jetbrains.python.sdk.PythonEnvUtil
import org.jetbrains.annotations.ApiStatus
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.function.Function
import kotlin.io.path.exists

@ApiStatus.Internal
open class PydevConsoleRunnerFactory : PyConsoleRunnerFactoryAsync() {
  @ApiStatus.Experimental
  protected sealed class ConsoleParameters(val project: Project,
                                           val sdk: Sdk?,
                                           @ApiStatus.ScheduledForRemoval
                                           @Deprecated("Use `ConstantConsoleParameters.workingDir`")
                                           open val workingDir: String?,
                                           val envs: Map<String, String>,
                                           val consoleType: PyConsoleType,
                                           val settingsProvider: PyConsoleSettings) {
    @ApiStatus.Internal
    var module: Module? = null
  }

  @ApiStatus.Experimental
  protected class ConstantConsoleParameters(project: Project,
                                            sdk: Sdk?,
                                            @Suppress("OVERRIDE_DEPRECATION") override val workingDir: String?,
                                            envs: Map<String, String>,
                                            consoleType: PyConsoleType,
                                            settingsProvider: PyConsoleSettings,
                                            val setupFragment: Array<String>)
    : ConsoleParameters(project, sdk, workingDir, envs, consoleType, settingsProvider)

  @ApiStatus.Experimental
  protected class TargetedConsoleParameters private constructor(project: Project,
                                                                sdk: Sdk?,
                                                                workingDir: String?,
                                                                val workingDirFunction: TargetEnvironmentFunction<String>?,
                                                                envs: Map<String, String>,
                                                                consoleType: PyConsoleType,
                                                                settingsProvider: PyConsoleSettings,
                                                                val setupScript: TargetEnvironmentFunction<String>)
    : ConsoleParameters(project, sdk, workingDir, envs, consoleType, settingsProvider) {
    constructor(project: Project, sdk: Sdk?, workingDirFunction: TargetEnvironmentFunction<String>?, envs: Map<String, String>,
                consoleType: PyConsoleType, settingsProvider: PyConsoleSettings, setupScript: TargetEnvironmentFunction<String>)
      : this(project, sdk, null, workingDirFunction, envs, consoleType, settingsProvider, setupScript)
  }

  /**
   * @deprecated override [createConsoleParametersAsync]. This one blocks, and choosing the interpreter waits for the
   * project model.
   *
   * Nothing inside this repository calls it, so an override here no longer decides what a console runs on.
   */
  @Deprecated("Blocks. Override createConsoleParametersAsync.", ReplaceWith("createConsoleParametersAsync(project, contextModule)"))
  @RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
  protected open fun createConsoleParameters(project: Project, contextModule: Module?): ConsoleParameters =
    runBlockingMaybeCancellable { createConsoleParametersAsync(project, contextModule) }

  protected open suspend fun createConsoleParametersAsync(project: Project, contextModule: Module?): ConsoleParameters {
    val sdkAndModule = findPythonSdkAndModule(project, contextModule)
    val module = sdkAndModule.second
    val sdk = sdkAndModule.first
    val settingsProvider = PyConsoleOptions.getInstance(project).pythonConsoleSettings
    val pathMapper = getPathMapper(project, sdk, settingsProvider)
    val envs = settingsProvider.envs.toMutableMap()
    putIPythonEnvFlag(project, envs)
    // Both read the module's content and source roots, and this now runs on a background thread rather than on the
    // EDT, which held read access on its own. This read action is what the former runReadActionBlocking around the
    // whole creation stood for.
    val (workingDirFunction, setupScriptFunction) = readAction {
      val workingDir = getWorkingDirFunction(project, module, pathMapper, settingsProvider)
      workingDir to createSetupScriptFunction(project, module, workingDir, pathMapper, settingsProvider)
    }
    return TargetedConsoleParameters(project, sdk, workingDirFunction, envs, PyConsoleType.PYTHON, settingsProvider, setupScriptFunction)
      .also { it.module = module }
  }

  override suspend fun createConsoleRunnerAsync(project: Project, contextModule: Module?): PydevConsoleRunner {
    val module = PyConsoleCustomizer.EP_NAME.extensionList.firstNotNullOfOrNull { it.guessConsoleModule(project, contextModule) }
    return when (val consoleParameters = createConsoleParametersAsync(project, module)) {
      is ConstantConsoleParameters -> PydevConsoleRunnerImpl(project, consoleParameters.sdk, consoleParameters.consoleType,
                                                             consoleParameters.workingDir,
                                                             consoleParameters.envs, consoleParameters.settingsProvider,
                                                             *consoleParameters.setupFragment)
      is TargetedConsoleParameters -> PydevConsoleRunnerImpl(project, consoleParameters.sdk, consoleParameters.consoleType,
                                                             consoleTabTitle(project, consoleParameters.module,
                                                                             consoleParameters.consoleType.title),
                                                             consoleParameters.workingDirFunction,
                                                             consoleParameters.envs, consoleParameters.settingsProvider,
                                                             consoleParameters.setupScript)
    }
  }

  override suspend fun createConsoleRunnerWithFileAsync(project: Project, config: PythonRunConfiguration): PydevConsoleRunner {
    val consoleParameters = createConsoleParametersAsync(project, config.module)
    val sdk = config.sdk ?: consoleParameters.sdk
    val consoleEnvs = mutableMapOf<String, String>()
    consoleEnvs.putAll(consoleParameters.envs)
    consoleEnvs.putAll(config.envs)
    return when (consoleParameters) {
      is ConstantConsoleParameters -> PydevConsoleWithFileRunnerImpl(project, sdk, consoleParameters.consoleType, config.name,
                                                                     config.workingDirectory ?: consoleParameters.workingDir, consoleEnvs,
                                                                     consoleParameters.settingsProvider, config,
                                                                     *consoleParameters.setupFragment)
      is TargetedConsoleParameters -> PydevConsoleWithFileRunnerImpl(project, sdk, consoleParameters.consoleType, config.name,
                                                                     config.workingDirectory?.let { targetPath(Path.of(it)) }
                                                                     ?: consoleParameters.workingDirFunction, consoleEnvs,
                                                                     consoleParameters.settingsProvider, config,
                                                                     consoleParameters.setupScript)
    }
  }

  companion object {
    fun putIPythonEnvFlag(project: Project, envs: MutableMap<String, String>) {
      putIPythonEnvFlag(project, PlainEnvironmentController(envs))
    }

    @JvmStatic
    fun putIPythonEnvFlag(project: Project, environmentController: EnvironmentController) {
      putIPythonEnvFlag(environmentController, PyConsoleOptions.getInstance(project).isIpythonEnabled)
    }

    /**
     * The Debug Console reads the same variable but keeps its own setting, so the debugger passes the value
     * of [PyConsoleOptions.isDebugConsoleIpythonEnabled] instead.
     */
    @ApiStatus.Internal
    @JvmStatic
    fun putDebugConsoleIPythonEnvFlag(project: Project, environmentController: EnvironmentController) {
      putIPythonEnvFlag(environmentController, PyDebuggerOptionsProvider.getInstance(project).isDebugConsoleIpythonEnabled)
    }

    private fun putIPythonEnvFlag(environmentController: EnvironmentController, enabled: Boolean) {
      environmentController.putFixedValue(PythonEnvUtil.IPYTHONENABLE, if (enabled) "True" else "False")
    }

    fun getWorkingDir(project: Project, module: Module?, pathMapper: PathMapper?, settingsProvider: PyConsoleSettings): String {
      var workingDir = getWorkingDirFromSettings(project, module, settingsProvider)
      if (pathMapper != null) {
        workingDir = pathMapper.convertToRemote(workingDir)
      }
      return workingDir
    }

    fun getWorkingDirFunction(project: Project,
                              module: Module?,
                              pathMapper: PathMapper?,
                              settingsProvider: PyConsoleSettings): TargetEnvironmentFunction<String>? {
      val workingDir = getWorkingDirFromSettings(project, module, settingsProvider)
      if (pathMapper != null && pathMapper.canReplaceLocal(workingDir)) {
        return constant(pathMapper.convertToRemote(workingDir))
      }

      var path: Path? = null
      try {
        path = workingDir.let { Path.of(it) }
      }
      catch (e: InvalidPathException) {
        thisLogger().warn(e)
      }
      if (path != null && !path.exists()) {
        thisLogger().warn("Can't find $path")
        path = null
      }
      return path?.let { targetPath(it) } ?: if (workingDir.isNotBlank()) constant(workingDir) else null
    }

    private fun getWorkingDirFromSettings(project: Project, module: Module?, settingsProvider: PyConsoleSettings): String {
      val workingDirectoryInSettings = settingsProvider.workingDirectory
      if (workingDirectoryInSettings.isNotEmpty()) {
        return workingDirectoryInSettings
      }
      if (module != null && ModuleRootManager.getInstance(module).contentRoots.isNotEmpty()) {
        return ModuleRootManager.getInstance(module).contentRoots[0].path
      }
      val projectRoots = ProjectRootManager.getInstance(project).contentRoots
      for (root in projectRoots) {
        if (root.fileSystem is LocalFileSystem) {
          // we can't start Python Console in remote folder without additional connection configurations
          return root.path
        }
      }
      return SystemProperties.getUserHome()
    }

    private fun makeStartWithEmptyLine(line: String): String {
      if (line.startsWith("\n") || line.isBlank()) return line
      return "\n" + line
    }

    private fun createSetupScriptFunction(project: Project,
                                          module: Module?,
                                          workingDir: TargetEnvironmentFunction<String>?,
                                          pathMapper: PyRemotePathMapper?,
                                          settingsProvider: PyConsoleSettings): TargetEnvironmentFunction<String> {
      val customStartScript = makeStartWithEmptyLine(settingsProvider.myCustomStartScript)
      val pythonPathFuns = collectPythonPath(project, module, settingsProvider.mySdkHome, pathMapper,
                                             settingsProvider.shouldAddContentRoots(), settingsProvider.shouldAddSourceRoots()
      ).toMutableSet()
      return constructPyPathAndWorkingDirCommand(pythonPathFuns, workingDir, customStartScript)
    }

    fun createSetupScriptWithHelpersAndProjectRoot(project: Project,
                                                   projectRoot: String,
                                                   interpreter: PythonInterpreter,
                                                   settingsProvider: PyConsoleSettings): TargetEnvironmentFunction<String> {
      val paths = ArrayList<Function<TargetEnvironment, String>>()
      paths.add(getTargetEnvironmentValueForLocalPath(Path.of(projectRoot)))

      val targetEnvironmentRequest = interpreter.targetEnvironmentRequest(project)
      val communityHelpers = targetEnvironmentRequest.preparePyCharmHelpers().helpers.find { it.localPath.endsWith("helpers") }
      if (communityHelpers != null) {
        for (helper in listOf("pycharm", "pydev")) {
          paths.add(communityHelpers.targetPathFun.getRelativeTargetPath(helper))
        }
      } else {
        Logger.getInstance(PydevConsoleRunnerFactory::class.java).error("Python Community helpers dir path not found")
      }

      val pathStr = paths.joinToStringFunction(separator = ", ", transform = String::toStringLiteral)
      val projectRootStr = getTargetEnvironmentValueForLocalPath(Path.of(projectRoot)).toStringLiteral()
      val replaces = listOf(PydevConsoleRunnerImpl.WORKING_DIR_AND_PYTHON_PATHS to pathStr,
                            PydevConsoleRunnerImpl.PROJECT_ROOT to projectRootStr)
      return ReplaceSubstringsFunction(makeStartWithEmptyLine(settingsProvider.myCustomStartScript), replaces)
    }
  }
}