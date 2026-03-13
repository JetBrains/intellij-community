// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.uv.sdk.configuration

import com.intellij.openapi.module.Module
import com.intellij.python.common.tools.ToolId
import com.intellij.python.community.impl.uv.common.UV_BASE_TOOL_ID
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.sdk.configuration.CreateSdkInfo
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.configuration.PyProjectTomlConfigurationExtension
import com.jetbrains.python.sdk.configuration.prepareSdkCreator

internal class PyUvBaseSdkConfiguration : PyProjectSdkConfigurationExtension {

  override val toolId: ToolId = UV_BASE_TOOL_ID

  override suspend fun checkEnvironmentAndPrepareSdkCreator(module: Module, venvsInModule: List<PythonBinary>): CreateSdkInfo? =
    prepareSdkCreator(
      { checkManageableUvEnvBase(venvsInModule) }
    ) { envExists -> { createUvSdk(module, toolId, venvsInModule, envExists) } }

  override fun asPyProjectTomlSdkConfigurationExtension(): PyProjectTomlConfigurationExtension? = null
}
