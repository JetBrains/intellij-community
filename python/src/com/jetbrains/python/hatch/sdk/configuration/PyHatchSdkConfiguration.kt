// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.hatch.sdk.configuration

import com.intellij.python.sdk.backend.PySdkBundle
import com.intellij.python.sdk.common.PyInterpreterRef
import com.intellij.python.sdk.backend.PythonInterpreterKey
import com.jetbrains.python.sdk.ModuleOrProject
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.intellij.python.sdk.backend.getPythonInfo
import com.intellij.python.sdk.backend.detectPythonEnvironment
import com.intellij.python.sdk.backend.resolvePythonBinary
import com.jetbrains.python.PythonInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.hatch.HATCH_TOML
import com.intellij.python.hatch.HatchExecutableNotFoundHatchError
import com.intellij.python.hatch.HatchPyTool
import com.intellij.python.hatch.HatchVirtualEnvironment
import com.intellij.python.pytools.resolveExecutable
import com.jetbrains.python.Result
import com.intellij.python.hatch.PythonVirtualEnvironment
import com.intellij.python.hatch.cli.HatchEnvironment
import com.intellij.python.hatch.getHatchService
import com.intellij.python.hatch.impl.HATCH_TOOL_ID
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.sdk.backend.PythonInterpreter
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.hatch.sdk.createSdk
import com.jetbrains.python.orLogException
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.getEel
import com.jetbrains.python.project.project
import com.jetbrains.python.sdk.add.v2.toFileSystem
import com.jetbrains.python.sdk.configuration.CheckToml
import com.jetbrains.python.sdk.configuration.CreateInterpreterInfo
import com.jetbrains.python.sdk.configuration.EnvCheckerResult
import com.jetbrains.python.sdk.configuration.EnvExists
import com.jetbrains.python.sdk.configuration.PyProjectTomlConfigurationExtension
import com.jetbrains.python.sdk.configuration.prepareSdkCreator
import com.jetbrains.python.util.runWithModalBlockingOrInBackground

internal class PyHatchSdkConfiguration : PyProjectTomlConfigurationExtension {
  companion object {
    private val LOGGER = Logger.getInstance(PyHatchSdkConfiguration::class.java)
  }

  override val toolId: ToolId = HATCH_TOOL_ID

  override val potentialDependencyFiles: Set<String> = setOf(PY_PROJECT_TOML, HATCH_TOML)

  override suspend fun checkEnvironmentAndPrepareSdkCreator(pyProject: PyProject, venvs: List<PythonBinary>): CreateInterpreterInfo? =
    prepareSdkCreator(
      { checkManageableEnv(pyProject, true) },
    ) { envExists -> { createSdk(pyProject, envExists) } }

  override suspend fun createSdkWithoutPyProjectTomlChecks(pyProject: PyProject, venvs: List<PythonBinary>): CreateInterpreterInfo? =
    prepareSdkCreator(
      { checkManageableEnv(pyProject, false) },
    ) { envExists -> { createSdk(pyProject, envExists) } }

  override fun asPyProjectTomlSdkConfigurationExtension(): PyProjectTomlConfigurationExtension = this

  /** The hatch environment that the entry names by its name, see `HatchSdkFlavor.toolEnvRefOf`. */
  override suspend fun prepareSdkCreatorFor(pyProject: PyProject, key: PythonInterpreterKey): CreateInterpreterInfo? {
    if (key.ref.manager != HatchPyTool.getInstance().fusId) return null
    when (key.ref.mode) {
      PyInterpreterRef.Mode.Native -> Unit
      is PyInterpreterRef.Mode.Target -> return null
    }
    val envName = key.ref.env.envRef.value
    return prepareSdkCreator(
      { EnvCheckerResult.EnvNotFound(PyBundle.message("sdk.recreate.hatch.environment", envName)) },
    ) { { createSdk(pyProject, envExists = false, envName = envName) } }
  }

  private suspend fun checkManageableEnv(
    pyProject: PyProject, checkToml: CheckToml,
  ): EnvCheckerResult = reportRawProgress {
    it.text(PyBundle.message("sdk.set.up.hatch.project.analysis"))
    val eel = pyProject.getEel()
    val hatchService = pyProject.baseDir.getHatchService(eel.toFileSystem()).getOr { return EnvCheckerResult.CannotConfigure }
    val canManage = if (checkToml) hatchService.isHatchManagedProject() else true
    val intentionName = PyBundle.message("sdk.set.up.hatch.environment")
    val envNotFound = EnvCheckerResult.EnvNotFound(intentionName)

    if (canManage) {
      val defaultEnv = hatchService.findDefaultVirtualEnvironmentOrNull().orLogException(LOGGER)?.pythonVirtualEnvironment
      when (defaultEnv) {
        // Asked of the one environment this is about. Listing them no longer runs every interpreter to learn a version
        // nobody had asked for, so the version for this one is read here — from what it records, and only otherwise by
        // running it.
        is PythonVirtualEnvironment.Existing -> {
          val info = defaultEnv.pythonInfo ?: defaultEnv.pythonHomePath.path.pythonInfoOrNull()
          if (info == null) envNotFound else EnvCheckerResult.EnvFound(info, intentionName)
        }
        is PythonVirtualEnvironment.NotExisting, null -> envNotFound
      }
    }
    else EnvCheckerResult.CannotConfigure
  }

  /**
   * Creates SDK for Hatch, it will also create a new Hatch environment and use an existing one.
   *
   * @param pyProject project used to create SDK
   * @param envExists shows whether the environment already exists or a new one should be created
   * @param envName the name of the hatch environment to create, or `null` for the default one
   */
  private fun createSdk(pyProject: PyProject, envExists: EnvExists, envName: String? = null): PyResult<PythonInterpreter> = runWithModalBlockingOrInBackground(
    project = pyProject.project,
    msg = PyBundle.message("sdk.set.up.hatch.environment")
  ) {
    val fileSystem = pyProject.getEel().toFileSystem()
    val hatchExecutablePath = HatchPyTool.getInstance().resolveExecutable(fileSystem)
                              ?: return@runWithModalBlockingOrInBackground Result.failure(HatchExecutableNotFoundHatchError(null))
    val hatchService = pyProject.baseDir.getHatchService(fileSystem, hatchExecutablePath).getOr { return@runWithModalBlockingOrInBackground it }

    // A named environment must still be declared in the project: hatch creates only what the project declares.
    val hatchEnvironment = if (envName == null) HatchEnvironment.DEFAULT
    else hatchService.findVirtualEnvironments().getOr { return@runWithModalBlockingOrInBackground it }
           .firstOrNull { it.hatchEnvironment.name == envName }?.hatchEnvironment
         ?: return@runWithModalBlockingOrInBackground PyResult.localizedError(PySdkBundle.message("evolution.error.env.not.found", envName))

    val environment = if (envExists) {
      val defaultEnv = hatchService.findDefaultVirtualEnvironmentOrNull()
        .mapSuccess { it?.pythonVirtualEnvironment }
        .getOr { return@runWithModalBlockingOrInBackground it }
      when (defaultEnv) {
        is PythonVirtualEnvironment.Existing -> defaultEnv
        is PythonVirtualEnvironment.NotExisting, null -> return@runWithModalBlockingOrInBackground PyResult.localizedError(
          PyBundle.message("sdk.could.not.find.valid.hatch.environment")
        )
      }
    }
    else {
      hatchService.createVirtualEnvironment(envName = envName).getOr { return@runWithModalBlockingOrInBackground it }
    }

    val hatchVenv = HatchVirtualEnvironment(hatchEnvironment, environment)
    hatchVenv.createSdk(
      moduleOrProject = ModuleOrProject.ModuleAndProject(pyProject),
      workingDirectoryPath = hatchService.getWorkingDirectoryPath(),
      fileSystem = fileSystem,
    )
  }

}

/**
 * What the environment at this home path is, without starting it where it says so itself.
 *
 * A hatch environment is a virtualenv, so its `pyvenv.cfg` states the version, which environment detection reads. The
 * interpreter is only run for one that records nothing.
 */
private suspend fun java.nio.file.Path.pythonInfoOrNull(): PythonInfo? {
  val binary = resolvePythonBinary() ?: return null
  val environment = withContext(Dispatchers.IO) { binary.detectPythonEnvironment().successOrNull } ?: return null
  return environment.getPythonInfo().successOrNull
}
