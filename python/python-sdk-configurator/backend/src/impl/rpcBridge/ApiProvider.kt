package com.intellij.python.sdkConfigurator.backend.impl.rpcBridge

import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.python.sdkConfigurator.common.SdkConfiguratorApi
import fleet.rpc.remoteApiDescriptor


internal class ApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<SdkConfiguratorApi>()) {
      SdkConfiguratorApiImpl
    }
  }
}
