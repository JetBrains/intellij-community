package com.intellij.python.sdkConfigurator.frontend.rpcBridge

import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.python.sdkConfigurator.common.SdkConfiguratorApi
import fleet.rpc.remoteApiDescriptor


internal suspend fun getBackendApi() = RemoteApiProviderService.resolve(remoteApiDescriptor<SdkConfiguratorApi>())

