// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.intellij.pycharm.community.ide.impl.findEnvOrNull
import com.intellij.python.common.tools.ToolId
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.projectCreation.createVenvAndSdk
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.configuration.*
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.flavors.VirtualEnvSdkFlavor
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.service.PySdkService.Companion.pySdkService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class PyVenvSdkConfiguration : PyProjectSdkConfigurationExtension {
  private val existingSdks by lazy { PythonSdkUtil.getAllSdks() }
  private val context = UserDataHolderBase()

  override val toolId: ToolId = VENV_TOOL_ID

  override suspend fun checkEnvironmentAndPrepareSdkCreator(module: Module): CreateSdkInfo? = prepareSdkCreator(
    { checkManageableEnv(module) }
  ) { envExists -> { setupVenv(module, envExists) } }

  override fun asPyProjectTomlSdkConfigurationExtension(): PyProjectTomlConfigurationExtension? = null

  private suspend fun checkManageableEnv(
    module: Module,
  ): EnvCheckerResult = withBackgroundProgress(module.project, PyBundle.message("python.sdk.validating.environment")) {
    withContext(Dispatchers.IO) {
      getVirtualEnv(module)?.let {
        it.findEnvOrNull(PyCharmCommunityCustomizationBundle.message("sdk.use.existing.venv", it.name))
      } ?: EnvCheckerResult.EnvNotFound(PyCharmCommunityCustomizationBundle.message("sdk.create.venv.suggestion.no.arg"))
    }
  }

  private fun getVirtualEnv(module: Module): PyDetectedSdk? = detectAssociatedEnvironments(module, existingSdks, context)
    .firstOrNull { !it.pyvenvContains("uv = ") }

  private suspend fun setupVenv(module: Module, envExists: EnvExists): PyResult<Sdk> =
    if (envExists) {
      setupExistingVenv(module)
    }
    else {
      createVenvAndSdk(ModuleOrProject.ModuleAndProject(module))
    }

  private suspend fun setupExistingVenv(module: Module): PyResult<Sdk> {
    val env = withContext(Dispatchers.IO) {
      getVirtualEnv(module)
    } ?: return PyResult.failure(MessageError("Can't find venv for the module"))

    val sdk = env.setupAssociated(
      existingSdks,
      module.basePath,
      true,
      PyFlavorAndData(PyFlavorData.Empty, VirtualEnvSdkFlavor.getInstance())
    ).getOr { return it }
    sdk.persist()
    module.project.pySdkService.persistSdk(sdk)

    return PyResult.success(sdk)
  }
}
