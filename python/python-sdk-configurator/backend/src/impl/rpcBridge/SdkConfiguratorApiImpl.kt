package com.intellij.python.sdkConfigurator.backend.impl.rpcBridge

import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.python.sdkConfigurator.backend.impl.ModuleConfigurationMode
import com.intellij.python.sdkConfigurator.backend.impl.ModulesSdkConfigurator.Companion.popModulesSDKConfigurator
import com.intellij.python.sdkConfigurator.backend.impl.configureSdk
import com.intellij.python.sdkConfigurator.common.impl.ModuleName
import com.intellij.python.sdkConfigurator.common.impl.SdkConfiguratorBackEndApi

internal object SdkConfiguratorApiImpl : SdkConfiguratorBackEndApi {
  override suspend fun configureSdkForModules(projectId: ProjectId, onlyModules: Set<ModuleName>) {
    projectId.findProject().popModulesSDKConfigurator().configureSdks(onlyModules)
  }

  override suspend fun configureAskingUser(projectId: ProjectId) {
    configureSdk(projectId.findProject(), mode = ModuleConfigurationMode.INTERACTIVE)
  }
}