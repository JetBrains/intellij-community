// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.util.MemoryDumpHelper
import com.intellij.util.SystemProperties
import com.jetbrains.performancePlugin.profilers.Profiler
import com.jetbrains.performancePlugin.profilers.ProfilerHandlerUtils
import java.io.File

class CaptureMemorySnapshotAction : DumbAwareAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val snapshotFolder = System.getProperty("snapshots.path", SystemProperties.getUserHome())
    val snapshotName = Profiler.formatSnapshotName(true) + ".hprof"
    val snapshotFile = File(snapshotFolder, snapshotName)
    try {
      MemoryDumpHelper.captureMemoryDump(snapshotFile.absolutePath)
      ProfilerHandlerUtils.notify(e.project, snapshotFile)
    }
    catch (exception: Exception) {
      ProfilerHandlerUtils.notifyCapturingError(exception, e.project)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = MemoryDumpHelper.memoryDumpAvailable()
  }
}