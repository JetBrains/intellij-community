package com.intellij.debugger.streams.backend

import com.intellij.debugger.streams.shared.StreamDebuggerApi
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor

internal class BackendStreamDebuggerRemoteApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<StreamDebuggerApi>()) {
      BackendStreamDebuggerApi()
    }
  }
}
