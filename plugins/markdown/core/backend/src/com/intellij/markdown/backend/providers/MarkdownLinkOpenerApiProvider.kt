package com.intellij.markdown.backend.providers

import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor
import com.intellij.markdown.backend.services.MarkdownLinkOpenerRemoteApiImpl
import org.intellij.plugins.markdown.service.MarkdownLinkOpenerRemoteApi

private class MarkdownLinkOpenerApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<MarkdownLinkOpenerRemoteApi>()) {
      MarkdownLinkOpenerRemoteApiImpl()
    }
  }
}