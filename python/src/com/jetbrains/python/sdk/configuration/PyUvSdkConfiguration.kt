// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.configuration

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.common.tools.ToolId
import com.intellij.python.community.impl.uv.common.UV_TOOL_ID
import com.intellij.python.pyproject.model.api.SuggestedSdk
import com.intellij.python.pyproject.model.api.suggestSdk
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.onSuccess
import com.jetbrains.python.sdk.baseDir
import com.jetbrains.python.sdk.persist
import com.jetbrains.python.sdk.pyvenvContains
import com.jetbrains.python.sdk.service.PySdkService.Companion.pySdkService
import com.jetbrains.python.sdk.setAssociationToModule
import com.jetbrains.python.sdk.uv.impl.getUvExecutable
import com.jetbrains.python.sdk.uv.setupExistingEnvAndSdk
import com.jetbrains.python.sdk.uv.setupNewUvSdkAndEnv
import com.jetbrains.python.venvReader.tryResolvePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

private val logger = fileLogger()

internal class PyUvSdkConfiguration : PyProjectTomlConfigurationExtension {
  override val toolId: ToolId = UV_TOOL_ID

  override suspend fun checkEnvironmentAndPrepareSdkCreator(module: Module, venvsInModule: List<PythonBinary>): CreateSdkInfo? =
    prepareSdkCreator(
      { checkManageableEnv(module, venvsInModule) }
    ) { envExists -> { createUv(module, venvsInModule, envExists) } }

  override suspend fun createSdkWithoutPyProjectTomlChecks(module: Module, venvsInModule: List<PythonBinary>): CreateSdkInfo? =
    prepareSdkCreator(
      { checkManageableEnv(module, venvsInModule) }
    ) { envExists -> { createUv(module, venvsInModule, envExists) } }

  override fun asPyProjectTomlSdkConfigurationExtension(): PyProjectTomlConfigurationExtension = this

  /**
   * This method checks whether uv environment exists and whether uv can manage the environment using the following logic:
   *   - If uv is not found on the system, the sdk cannot be configured with uv
   *   - If pyproject.toml check is required
   *     - If pyproject.toml file is found, we check whether we can manage this project
   *     - If there's no pyproject.toml, we assume that we cannot configure the project however,
   *       if we found existing uv environment, we will use it
   *   - If pyproject.toml check shouldn't be performed, then we just check whether the environment exists
   */
  private suspend fun checkManageableEnv(
    module: Module,
    venvsInModule: List<PythonBinary>,
  ): EnvCheckerResult {
    getUvExecutable() ?: return EnvCheckerResult.CannotConfigure
    val intentionName = PyBundle.message("sdk.set.up.uv.environment", module.name)
    val envFound = getUvEnv(venvsInModule)?.findEnvOrNull(intentionName)
    return envFound ?: EnvCheckerResult.EnvNotFound(intentionName)
  }

  private suspend fun getUvEnv(venvsInModule: List<PythonBinary>): PythonBinary? = venvsInModule.firstOrNull {
    it.pyvenvContains("uv = ")
  }

  private suspend fun Module.getSdkAssociatedModule() =
    when (val r = suggestSdk()) {
      // Workspace suggested by uv
      is SuggestedSdk.SameAs -> if (r.accordingTo == toolId) r.parentModule else null
      null, is SuggestedSdk.PyProjectIndependent -> null
    } ?: this

  private suspend fun createUv(module: Module, venvsInModule: List<PythonBinary>, envExists: Boolean): PyResult<Sdk> {
    val sdkAssociatedModule = module.getSdkAssociatedModule()
    val workingDir: Path? = tryResolvePath(sdkAssociatedModule.baseDir?.path)
    if (workingDir == null) {
      throw IllegalStateException("Can't determine working dir for the module")
    }

    val sdkSetupResult = if (envExists) {
      getUvEnv(venvsInModule)?.let {
        setupExistingEnvAndSdk(it, workingDir, false, workingDir)
      } ?: run {
        logger.warn("Can't find existing uv environment in project, but it was expected. " +
                    "Probably it was deleted. New environment will be created")
        setupNewUvSdkAndEnv(workingDir, null)
      }
    }
    else setupNewUvSdkAndEnv(workingDir, null)

    sdkSetupResult.onSuccess {
      withContext(Dispatchers.EDT) {
        it.persist()
        it.setAssociationToModule(sdkAssociatedModule)
        sdkAssociatedModule.project.pySdkService.persistSdk(it)
      }
    }
    return sdkSetupResult
  }
}
