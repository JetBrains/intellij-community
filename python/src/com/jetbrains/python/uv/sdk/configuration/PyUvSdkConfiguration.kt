// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.uv.sdk.configuration

import com.intellij.openapi.module.Module
import com.intellij.python.common.tools.ToolId
import com.intellij.python.community.impl.uv.common.UV_TOOL_ID
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.sdk.configuration.CreateSdkInfo
import com.jetbrains.python.sdk.configuration.EnvCheckerResult
import com.jetbrains.python.sdk.configuration.PyProjectTomlConfigurationExtension
import com.jetbrains.python.sdk.configuration.prepareSdkCreator
import com.jetbrains.python.sdk.uv.impl.setUvExecutableLocal
import com.jetbrains.python.uv.findUvLock
import java.nio.file.Path

internal class PyUvSdkConfiguration : PyProjectTomlConfigurationExtension {
  override val toolId: ToolId = UV_TOOL_ID

  override suspend fun checkEnvironmentAndPrepareSdkCreator(module: Module, venvsInModule: List<PythonBinary>): CreateSdkInfo? =
    prepareSdkCreator(
      { checkManageableUvEnvWithUvLock(module, venvsInModule, tomlCheckedByWorkspaceTools = false) }
    ) { envExists -> { createUvSdk(module, toolId, venvsInModule, envExists) } }

  override suspend fun createSdkWithoutPyProjectTomlChecks(module: Module, venvsInModule: List<PythonBinary>): CreateSdkInfo? =
    prepareSdkCreator(
      { checkManageableUvEnvWithUvLock(module, venvsInModule, tomlCheckedByWorkspaceTools = true) }
    ) { envExists -> { createUvSdk(module, toolId, venvsInModule, envExists) } }

  private suspend fun checkManageableUvEnvWithUvLock(
    module: Module,
    venvsInModule: List<PythonBinary>,
    tomlCheckedByWorkspaceTools: Boolean
  ): EnvCheckerResult {
    val baseCheckResult = checkManageableUvEnvBase(module, venvsInModule)
    return when (baseCheckResult) {
      is EnvCheckerResult.EnvFound, is EnvCheckerResult.SuggestToolInstallation -> baseCheckResult
      is EnvCheckerResult.EnvNotFound -> if (tomlCheckedByWorkspaceTools || findUvLock(module) != null) baseCheckResult else EnvCheckerResult.CannotConfigure
      is EnvCheckerResult.CannotConfigure -> if (findUvLock(module) != null) {
        val pathPersister: (Path) -> Unit = { setUvExecutableLocal(it) }
        val toolName = "uv"
        EnvCheckerResult.SuggestToolInstallation(
          toolToInstall = toolName,
          pathPersister = pathPersister,
          intentionName = PyBundle.message("sdk.create.custom.venv.install.fix.title.using.pip", toolName)
        )
      } else baseCheckResult
    }
  }

  override fun asPyProjectTomlSdkConfigurationExtension(): PyProjectTomlConfigurationExtension = this
}
