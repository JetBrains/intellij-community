// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.configuration

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.refreshAndFindVirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.python.common.tools.ToolId
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.projectCreation.createVenvAndSdk
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.flavors.VirtualEnvSdkFlavor
import com.jetbrains.python.sdk.impl.resolvePythonHome
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.service.PySdkService.Companion.pySdkService
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

  private suspend fun getVirtualEnv(venvsInModule: List<PythonBinary>): PythonBinary? = venvsInModule.firstOrNull {
    !it.pyvenvContains("uv = ")
  }

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
    } ?: return PyResult.failure(MessageError("Can't find venv for the module"))

    val pyDetectedSdk = PyDetectedSdk(pythonBinary.toString())
    val sdk = pyDetectedSdk.setupAssociated(
      PythonSdkUtil.getAllSdks(),
      module.baseDir?.path,
      true,
      PyFlavorAndData(PyFlavorData.Empty, VirtualEnvSdkFlavor.getInstance())
    ).getOr { return it }
    sdk.persist()
    module.project.pySdkService.persistSdk(sdk)

    return PyResult.success(sdk)
  }
}
