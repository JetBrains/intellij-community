package com.jetbrains.python.sdk.configuration

import com.intellij.openapi.module.Module
import com.jetbrains.python.ToolId
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PyProjectTomlConfigurationExtension : PyProjectSdkConfigurationExtension {

  suspend fun createSdkWithoutPyProjectTomlChecks(module: Module): CreateSdkInfo?
}