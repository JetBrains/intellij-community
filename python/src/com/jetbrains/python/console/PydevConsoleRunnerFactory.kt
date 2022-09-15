// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console

import com.intellij.execution.target.value.TargetEnvironmentFunction
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

open class PydevConsoleRunnerFactory : PythonConsoleRunnerFactory() {
  protected sealed class ConsoleParameters(val project: Project,
                                           val sdk: Sdk?,
                                           val workingDir: String?,
                                           val envs: Map<String, String>,
                                           val consoleType: PyConsoleType,
                                           val settingsProvider: PyConsoleSettings)

  protected class ConstantConsoleParameters(project: Project,
                                            sdk: Sdk?,
                                            workingDir: String?,
                                            envs: Map<String, String>,
                                            consoleType: PyConsoleType,
                                            settingsProvider: PyConsoleSettings,
                                            val setupFragment: Array<String>)
    : ConsoleParameters(project, sdk, workingDir, envs, consoleType, settingsProvider)

  protected class TargetedConsoleParameters(project: Project,
                                            sdk: Sdk?,
                                            workingDir: String?,
                                            envs: Map<String, String>,
                                            consoleType: PyConsoleType,
                                            settingsProvider: PyConsoleSettings,
                                            val setupScript: TargetEnvironmentFunction<String>)
    : ConsoleParameters(project, sdk, workingDir, envs, consoleType, settingsProvider)

  protected open fun createConsoleParameters(project: Project, contextModule: Module?): ConsoleParameters {
    val sdkAndModule = findPythonSdkAndModule(project, contextModule)
    val module = sdkAndModule.second
    val sdk = sdkAndModule.first
    val settingsProvider = PyConsoleOptions.getInstance(project).pythonConsoleSettings
    val pathMapper = getPathMapper(project, sdk, settingsProvider)
    val workingDir = getWorkingDir(project, module, pathMapper, settingsProvider)
    val envs = settingsProvider.envs.toMutableMap()
    putIPythonEnvFlag(project, envs)
    if (Registry.`is`("python.use.targets.api")) {
      val setupScriptFunction = createSetupScriptFunction(project, module, workingDir, pathMapper, settingsProvider)
      return TargetedConsoleParameters(project, sdk, workingDir, envs, PyConsoleType.PYTHON, settingsProvider, setupScriptFunction)
    }
    else {
      val setupFragment = createSetupFragment(module, workingDir, pathMapper, settingsProvider)
      return ConstantConsoleParameters(project, sdk, workingDir, envs, PyConsoleType.PYTHON, settingsProvider, setupFragment)
    }
  }

  override fun createConsoleRunner(project: Project, contextModule: Module?): PydevConsoleRunner =
    when (val consoleParameters = createConsoleParameters(project, contextModule)) {
      is ConstantConsoleParameters -> PydevConsoleRunnerImpl(project, consoleParameters.sdk, consoleParameters.consoleType,
                                                             consoleParameters.workingDir,
                                                             consoleParameters.envs, consoleParameters.settingsProvider,
                                                             *consoleParameters.setupFragment)
      is TargetedConsoleParameters -> PydevConsoleRunnerImpl(project, consoleParameters.sdk, consoleParameters.consoleType,
                                                             consoleParameters.consoleType.title,
                                                             consoleParameters.workingDir,
                                                             consoleParameters.envs, consoleParameters.settingsProvider,
                                                             consoleParameters.setupScript)
    }

  override fun createConsoleRunnerWithFile(project: Project, config: PythonRunConfiguration): PydevConsoleRunner {
    val consoleParameters = createConsoleParameters(project, config.module)
    val sdk = if (config.sdk != null) config.sdk else consoleParameters.sdk
    val workingDir = if (config.workingDirectory != null) config.workingDirectory else consoleParameters.workingDir
    val consoleEnvs = mutableMapOf<String, String>()
    consoleEnvs.putAll(consoleParameters.envs)
    consoleEnvs.putAll(config.envs)
    return when (consoleParameters) {
      is ConstantConsoleParameters -> PydevConsoleWithFileRunnerImpl(project, sdk, consoleParameters.consoleType, config.name, workingDir,
                                                                     consoleEnvs, consoleParameters.settingsProvider, config,
                                                                     *consoleParameters.setupFragment)
      is TargetedConsoleParameters -> PydevConsoleWithFileRunnerImpl(project, sdk, consoleParameters.consoleType, config.name, workingDir,
                                                                     consoleEnvs, consoleParameters.settingsProvider, config,
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
      var workingDir = settingsProvider.workingDirectory
      if (workingDir.isNullOrEmpty()) {
        if (module != null && ModuleRootManager.getInstance(module).contentRoots.isNotEmpty()) {
          workingDir = ModuleRootManager.getInstance(module).contentRoots[0].path
        }
        else {
          val projectRoots = ProjectRootManager.getInstance(project).contentRoots
          for (root in projectRoots) {
            if (root.fileSystem is LocalFileSystem) {
              // we can't start Python Console in remote folder without additional connection configurations
              workingDir = root.path
              break
            }
          }
        }
      }
      if (workingDir.isNullOrEmpty()) {
        workingDir = System.getProperty("user.home")
      }
      if (pathMapper != null && workingDir != null) {
        workingDir = pathMapper.convertToRemote(workingDir)
      }
      return workingDir
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
                                          workingDir: String?,
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