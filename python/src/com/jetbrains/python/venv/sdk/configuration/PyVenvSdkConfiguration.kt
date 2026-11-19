// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.venv.sdk.configuration

import com.intellij.python.sdk.backend.PIP_MANAGER
import com.intellij.python.sdk.backend.PythonInterpreterKey
import com.jetbrains.python.sdk.configuration.nativeHomeOf
import com.intellij.openapi.vfs.refreshAndFindVirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.venv.createVenvAdditionalData
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.setupPy.SetupPyHelpers.SETUP_PY
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.project
import com.jetbrains.python.projectCreation.createVenvAndSdk
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.configuration.CreateInterpreterInfo
import com.jetbrains.python.sdk.configuration.EnvCheckerResult
import com.jetbrains.python.sdk.configuration.EnvExists
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.configuration.PyProjectTomlConfigurationExtension
import com.jetbrains.python.sdk.configuration.VENV_TOOL_ID
import com.jetbrains.python.sdk.configuration.findEnvOrNull
import com.jetbrains.python.sdk.configuration.prepareSdkCreator
import com.jetbrains.python.sdk.createSdk
import com.intellij.python.venv.environment.isUvEnv
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.path.name

internal class PyVenvSdkConfiguration : PyProjectSdkConfigurationExtension {
  override val toolId: ToolId = VENV_TOOL_ID
  override val potentialDependencyFiles: Set<String> = setOf(PythonSdkAdditionalData.REQUIREMENT_TXT_DEFAULT.fileName.toString(), SETUP_PY)

  override suspend fun checkEnvironmentAndPrepareSdkCreator(pyProject: PyProject, venvs: List<PythonBinary>): CreateInterpreterInfo? =
    prepareSdkCreator(
      { checkManageableEnv(pyProject, venvs) }
    ) { envExists -> { setupVenv(pyProject, venvs, envExists) } }

  override fun asPyProjectTomlSdkConfigurationExtension(): PyProjectTomlConfigurationExtension? = null

  /**
   * A virtual environment that the entry places at its home, see [nativeHomeOf]. An environment that no tool manages has
   * the pip manager.
   */
  override suspend fun prepareSdkCreatorFor(pyProject: PyProject, key: PythonInterpreterKey): CreateInterpreterInfo? {
    if (key.ref.manager != PIP_MANAGER) return null
    val home = nativeHomeOf(key) ?: return null
    return prepareSdkCreator(
      { EnvCheckerResult.EnvNotFound(PyBundle.message("sdk.recreate.venv.suggestion", home.fileName)) }
    ) { { createVenvAndSdk(ModuleOrProject.ModuleAndProject(pyProject), venvDir = home) } }
  }

  private suspend fun checkManageableEnv(
    pyProject: PyProject,
    venvs: List<PythonBinary>,
  ): EnvCheckerResult = withBackgroundProgress(pyProject.project, PyBundle.message("python.sdk.validating.environment")) {
    withContext(Dispatchers.IO) {
      getVirtualEnv(venvs)?.let {
        it.findEnvOrNull(PyBundle.message("sdk.use.existing.venv", VirtualEnvReader().resolvePythonHomeFromPythonBinary(it).name))
      } ?: EnvCheckerResult.EnvNotFound(PyBundle.message("sdk.create.venv.suggestion.no.arg"))
    }
  }

  private fun getVirtualEnv(venvs: List<PythonBinary>): PythonBinary? = venvs.firstOrNull { !it.isUvEnv() }

  private suspend fun setupVenv(pyProject: PyProject, venvs: List<PythonBinary>, envExists: EnvExists): PyResult<PythonInterpreter> =
    if (envExists) {
      setupExistingVenv(pyProject, venvs)
    }
    else {
      createVenvAndSdk(ModuleOrProject.ModuleAndProject(pyProject))
    }

  private suspend fun setupExistingVenv(pyProject: PyProject, venvs: List<PythonBinary>): PyResult<PythonInterpreter> {
    val pythonBinary = withContext(Dispatchers.IO) {
      getVirtualEnv(venvs)?.refreshAndFindVirtualFile()
    } ?: return PyResult.failure(MessageError(PyBundle.message("sdk.cannot.find.venv.for.module")))

    val additionalData = createVenvAdditionalData(pyProject.baseDir)
    val pythonInterpreter = withContext(Dispatchers.IO) {
      createSdk(
        pyProject,
        PathHolder.Eel(pythonBinary.toNioPath()),
        additionalData,
        null,
      )
    }.getOr { return it }

    return PyResult.success(pythonInterpreter)
  }
}
