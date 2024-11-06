package com.intellij.performanceTesting.backend.split

import com.intellij.idea.AppMode
import com.intellij.openapi.client.ClientSessionsManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.jetbrains.performancePlugin.PerformanceTestingBundle
import com.jetbrains.performancePlugin.profilers.SnapshotOpener
import com.jetbrains.rd.ide.model.RemoteFileAddress
import com.jetbrains.rd.ide.model.SplitIde
import com.jetbrains.rd.ide.model.fileTransferModel
import com.jetbrains.rdserver.core.protocolModel
import org.jetbrains.annotations.Nls
import java.io.File

internal class RemoteClientSnapshotUploader : SnapshotOpener {
  override fun canOpen(snapshot: File, project: Project?): Boolean {
    return project != null && AppMode.isRemoteDevHost()
  }

  override fun getPresentableName(): @Nls String? {
    return PerformanceTestingBundle.message("profiling.load.snapshot.to.client.action.text")
  }

  override fun open(snapshot: File, project: Project?) {
    if (project == null) {
      thisLogger().warn("Unable to load a snapshot without a project")
      return
    }

    val remoteFileAddress = RemoteFileAddress(snapshot.absolutePath, SplitIde.Host)
    ClientSessionsManager.getProjectSession(project)?.protocolModel?.fileTransferModel?.chooseTargetDirectoryAndDownloadFile?.fire(listOf(remoteFileAddress))
  }
}