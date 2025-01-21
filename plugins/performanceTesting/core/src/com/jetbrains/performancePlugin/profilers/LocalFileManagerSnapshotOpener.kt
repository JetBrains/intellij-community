package com.jetbrains.performancePlugin.profilers

import com.intellij.ide.actions.RevealFileAction
import com.intellij.idea.AppMode
import com.intellij.openapi.project.Project
import com.jetbrains.performancePlugin.PerformanceTestingBundle
import java.io.File

internal class LocalFileManagerSnapshotOpener : SnapshotOpener {
  override fun canOpen(snapshot: File, project: Project?): Boolean {
    return !AppMode.isRemoteDevHost()
  }

  override fun getPresentableName(): String? {
    return PerformanceTestingBundle.message("profiling.capture.snapshot.action.showInFolder", RevealFileAction.getFileManagerName())
  }

  override fun open(snapshot: File, project: Project?) {
    RevealFileAction.openFile(snapshot)
  }
}