package com.intellij.python.sdkConfigurator.backend.impl.rpcBridge

import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.python.sdkConfigurator.backend.impl.configureSdkAutomatically
import com.intellij.python.sdkConfigurator.common.impl.ModuleName
import com.intellij.python.sdkConfigurator.common.impl.SdkConfiguratorBackEndApi

internal object SdkConfiguratorApiImpl : SdkConfiguratorBackEndApi {
  override suspend fun configureSdkAutomatically(projectId: ProjectId, onlyModules: Set<ModuleName>) {
    configureSdkAutomatically(projectId.findProject(), onlyModules)
  }
}