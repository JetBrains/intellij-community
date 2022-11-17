// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console

import com.intellij.execution.target.value.TargetEnvironmentFunction
import com.intellij.execution.target.value.constant
import com.intellij.execution.target.value.targetPath
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.PathMapper
import com.jetbrains.python.console.PyConsoleOptions.PyConsoleSettings
import com.jetbrains.python.remote.PyRemotePathMapper
import com.jetbrains.python.run.*
import com.jetbrains.python.sdk.PythonEnvUtil
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

open class PydevConsoleRunnerFactory : PythonConsoleRunnerFactory() {
  @ApiStatus.Experimental
  protected sealed class ConsoleParameters(val project: Project,
                                           val sdk: Sdk?,
                                           @ApiStatus.ScheduledForRemoval
                                           @Deprecated("Use `ConstantConsoleParameters.workingDir`")
                                           open val workingDir: String?,
                                           val envs: Map<String, String>,
                                           val consoleType: PyConsoleType,
                                           val settingsProvider: PyConsoleSettings)

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

    @Deprecated("Use another constructor")
    @ApiStatus.ScheduledForRemoval
    constructor(project: Project, sdk: Sdk?, workingDir: String?, envs: Map<String, String>, consoleType: PyConsoleType,
                settingsProvider: PyConsoleSettings, setupScript: TargetEnvironmentFunction<String>)
      : this(project, sdk, workingDir, workingDir?.let { constant(it) }, envs, consoleType, settingsProvider, setupScript)
  }

  protected open fun createConsoleParameters(project: Project, contextModule: Module?): ConsoleParameters {
    val sdkAndModule = findPythonSdkAndModule(project, contextModule)
    val module = sdkAndModule.second
    val sdk = sdkAndModule.first
    val settingsProvider = PyConsoleOptions.getInstance(project).pythonConsoleSettings
    val pathMapper = getPathMapper(project, sdk, settingsProvider)
    val envs = settingsProvider.envs.toMutableMap()
    putIPythonEnvFlag(project, envs)
    if (Registry.`is`("python.use.targets.api")) {
      val workingDirFunction = getWorkingDirFunction(project, module, pathMapper, settingsProvider)
      val setupScriptFunction = createSetupScriptFunction(project, module, workingDirFunction, pathMapper, settingsProvider)
      return TargetedConsoleParameters(project, sdk, workingDirFunction, envs, PyConsoleType.PYTHON, settingsProvider, setupScriptFunction)
    }
    else {
      val workingDir = getWorkingDir(project, module, pathMapper, settingsProvider)
      val setupFragment = createSetupFragment(module, workingDir, pathMapper, settingsProvider)
      return ConstantConsoleParameters(project, sdk, workingDir, envs, PyConsoleType.PYTHON, settingsProvider, setupFragment)
    }
  }

  override fun createConsoleRunner(project: Project, contextModule: Module?): PydevConsoleRunner {
    var module = contextModule
    if (module == null) {
      module = PyConsoleCustomizer.EP_NAME.extensionList.firstNotNullOfOrNull { it.guessConsoleModule(project) }
    }
    return when (val consoleParameters = createConsoleParameters(project, module)) {
      is ConstantConsoleParameters -> PydevConsoleRunnerImpl(project, consoleParameters.sdk, consoleParameters.consoleType,
                                                             consoleParameters.workingDir,
                                                             consoleParameters.envs, consoleParameters.settingsProvider,
                                                             *consoleParameters.setupFragment)
      is TargetedConsoleParameters -> PydevConsoleRunnerImpl(project, consoleParameters.sdk, consoleParameters.consoleType,
                                                             consoleParameters.consoleType.title,
                                                             consoleParameters.workingDirFunction,
                                                             consoleParameters.envs, consoleParameters.settingsProvider,
                                                             consoleParameters.setupScript)
    }
  }

  override fun createConsoleRunnerWithFile(project: Project, config: PythonRunConfiguration): PydevConsoleRunner {
    val consoleParameters = createConsoleParameters(project, config.module)
    val sdk = if (config.sdk != null) config.sdk else consoleParameters.sdk
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
      val ipythonEnabled = if (PyConsoleOptions.getInstance(project).isIpythonEnabled) "True" else "False"
      environmentController.putFixedValue(PythonEnvUtil.IPYTHONENABLE, ipythonEnabled)
    }

    fun getWorkingDir(project: Project, module: Module?, pathMapper: PathMapper?, settingsProvider: PyConsoleSettings): String? {
      var workingDir = getWorkingDirFromSettings(project, module, settingsProvider)
      if (pathMapper != null && workingDir != null) {
        workingDir = pathMapper.convertToRemote(workingDir)
      }
      return workingDir
    }

    private fun getWorkingDirFunction(project: Project,
                                      module: Module?,
                                      pathMapper: PathMapper?,
                                      settingsProvider: PyConsoleSettings): TargetEnvironmentFunction<String>? {
      val workingDir = getWorkingDirFromSettings(project, module, settingsProvider)
      if (pathMapper != null && workingDir != null && pathMapper.canReplaceLocal(workingDir)) {
        return constant(pathMapper.convertToRemote(workingDir))
      }
      return if (workingDir.isNullOrEmpty()) null else targetPath(Path.of(workingDir))
    }

    private fun getWorkingDirFromSettings(project: Project, module: Module?, settingsProvider: PyConsoleSettings): String? {
      val workingDirectoryInSettings = settingsProvider.workingDirectory
      if (!workingDirectoryInSettings.isNullOrEmpty()) {
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
      return System.getProperty("user.home")
    }

    fun createSetupFragment(module: Module?,
                            workingDir: String?,
                            pathMapper: PathMapper?,
                            settingsProvider: PyConsoleSettings): Array<String> {
      var customStartScript = settingsProvider.customStartScript
      if (customStartScript.isNotBlank()) {
        customStartScript = "\n" + customStartScript
      }
      var pythonPath = PythonCommandLineState.collectPythonPath(module, settingsProvider.shouldAddContentRoots(),
                                                                settingsProvider.shouldAddSourceRoots())
      if (pathMapper != null) {
        pythonPath = pathMapper.convertToRemote(pythonPath)
      }
      val selfPathAppend = constructPyPathAndWorkingDirCommand(pythonPath, workingDir, customStartScript)
      return arrayOf(selfPathAppend)
    }

    private fun createSetupScriptFunction(project: Project,
                                          module: Module?,
                                          workingDir: TargetEnvironmentFunction<String>?,
                                          pathMapper: PyRemotePathMapper?,
                                          settingsProvider: PyConsoleSettings): TargetEnvironmentFunction<String> {
      var customStartScript = settingsProvider.customStartScript
      if (customStartScript.isNotBlank()) {
        customStartScript = "\n" + customStartScript
      }
      val pythonPathFuns = collectPythonPath(project, module, settingsProvider.mySdkHome, pathMapper,
                                             settingsProvider.shouldAddContentRoots(), settingsProvider.shouldAddSourceRoots(),
                                             false).toMutableSet()
      return constructPyPathAndWorkingDirCommand(pythonPathFuns, workingDir, customStartScript)
    }
  }
}