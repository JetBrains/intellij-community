package com.intellij.markdown.backend.providers

import com.intellij.markdown.backend.services.VirtualFileAccessorImpl
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor
import org.intellij.plugins.markdown.service.VirtualFileAccessor

internal class VirtualFileAccessorProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<VirtualFileAccessor>()) {
      VirtualFileAccessorImpl()
    }
  }
}