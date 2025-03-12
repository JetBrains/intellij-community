package org.intellij.plugins.markdown.backend.providers

import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor
import org.intellij.plugins.markdown.backend.services.MarkdownLinkOpenerRemoteApiImpl
import org.intellij.plugins.markdown.service.MarkdownLinkOpenerRemoteApi

private class LinkOpenerApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<MarkdownLinkOpenerRemoteApi>()) {
      MarkdownLinkOpenerRemoteApiImpl()
    }
  }
}