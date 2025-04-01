package org.intellij.plugins.markdown.backend.providers

import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor
import org.intellij.plugins.markdown.backend.services.ProjectStructureRemoteApiImpl
import org.intellij.plugins.markdown.service.ProjectStructureRemoteApi

private class ProjectStructureProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<ProjectStructureRemoteApi>()) {
      ProjectStructureRemoteApiImpl()
    }
  }
}