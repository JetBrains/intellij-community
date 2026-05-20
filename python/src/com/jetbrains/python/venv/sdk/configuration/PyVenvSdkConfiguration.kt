// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.venv.sdk.configuration

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.refreshAndFindVirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.python.common.tools.ToolId
import com.intellij.python.venv.createVenvAdditionalData
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.projectCreation.createVenvAndSdk
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.configuration.CreateSdkInfo
import com.jetbrains.python.sdk.configuration.EnvCheckerResult
import com.jetbrains.python.sdk.configuration.EnvExists
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.configuration.PyProjectTomlConfigurationExtension
import com.jetbrains.python.sdk.configuration.VENV_TOOL_ID
import com.jetbrains.python.sdk.configuration.findEnvOrNull
import com.jetbrains.python.sdk.configuration.prepareSdkCreator
import com.jetbrains.python.sdk.createSdk
import com.jetbrains.python.sdk.impl.resolvePythonHome
import com.jetbrains.python.sdk.setAssociationToModule
import com.jetbrains.python.uv.sdk.configuration.isUvEnv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.path.name

internal class PyVenvSdkConfiguration : PyProjectSdkConfigurationExtension {
  override val toolId: ToolId = VENV_TOOL_ID

  override suspend fun checkEnvironmentAndPrepareSdkCreator(module: Module, venvsInModule: List<PythonBinary>): CreateSdkInfo? =
    prepareSdkCreator(
      { checkManageableEnv(module, venvsInModule) }
    ) { envExists -> { setupVenv(module, venvsInModule, envExists) } }

  override fun asPyProjectTomlSdkConfigurationExtension(): PyProjectTomlConfigurationExtension? = null

  private suspend fun checkManageableEnv(
    module: Module,
    venvsInModule: List<PythonBinary>,
  ): EnvCheckerResult = withBackgroundProgress(module.project, PyBundle.message("python.sdk.validating.environment")) {
    withContext(Dispatchers.IO) {
      getVirtualEnv(venvsInModule)?.let {
        it.findEnvOrNull(PyBundle.message("sdk.use.existing.venv", it.resolvePythonHome().name))
      } ?: EnvCheckerResult.EnvNotFound(PyBundle.message("sdk.create.venv.suggestion.no.arg"))
    }
  }

  private fun getVirtualEnv(venvsInModule: List<PythonBinary>): PythonBinary? = venvsInModule.firstOrNull { !it.isUvEnv() }

  private suspend fun setupVenv(module: Module, venvsInModule: List<PythonBinary>, envExists: EnvExists): PyResult<Sdk> =
    if (envExists) {
      setupExistingVenv(module, venvsInModule)
    }
    else {
      createVenvAndSdk(ModuleOrProject.ModuleAndProject(module))
    }

  private suspend fun setupExistingVenv(module: Module, venvsInModule: List<PythonBinary>): PyResult<Sdk> {
    val pythonBinary = withContext(Dispatchers.IO) {
      getVirtualEnv(venvsInModule)?.refreshAndFindVirtualFile()
    } ?: return PyResult.failure(MessageError(PyBundle.message("sdk.cannot.find.venv.for.module")))

    val sdk = withContext(Dispatchers.IO) {
      createSdk(
        PathHolder.Eel(pythonBinary.toNioPath()),
        createVenvAdditionalData(),
        null,
      )
    }.getOr { return it }

    sdk.setAssociationToModule(module)

    return PyResult.success(sdk)
  }
}
