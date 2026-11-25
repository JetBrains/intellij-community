// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.actions

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.platform.ide.progress.BackgroundTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.ui.progress.ProgressUIUtil
import com.intellij.util.MemoryDumpHelper
import com.intellij.util.SystemProperties
import com.jetbrains.performancePlugin.PerformanceTestingBundle
import com.jetbrains.performancePlugin.profilers.Profiler
import com.jetbrains.performancePlugin.profilers.ProfilerHandlerUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

private val LOG = logger<CaptureMemorySnapshotAction>()

/**
 * The time from the start of the background task to the start of the heap dump.
 * The background progress appears after [ProgressUIUtil.DEFAULT_PROGRESS_DELAY_MILLIS].
 * The extra time lets the warning balloon and the progress paint before the heap dump stops the JVM threads at a safepoint.
 */
private val PAINT_DELAY = (ProgressUIUtil.DEFAULT_PROGRESS_DELAY_MILLIS + 200).milliseconds

/**
 * Captures an `.hprof` heap dump of the IDE.
 *
 * The IDE does not respond while the JVM writes the heap objects at a safepoint.
 * The action shows a warning balloon and background progress before the dump starts, and it runs the dump on a background thread.
 */
class CaptureMemorySnapshotAction : DumbAwareAction() {
  private val capturing = AtomicBoolean()

  override fun actionPerformed(e: AnActionEvent) {
    if (!capturing.compareAndSet(false, true)) {
      return
    }

    val project = e.project
    val snapshotFolder = System.getProperty("snapshots.path", SystemProperties.getUserHome())
    val snapshotName = Profiler.formatSnapshotName(true) + ".hprof"
    val snapshotFile = File(snapshotFolder, snapshotName)
    val owner = if (project != null) BackgroundTaskOwner.project(project) else BackgroundTaskOwner.guess()

    val warning = Notification("PerformancePlugin",
                               PerformanceTestingBundle.message("profiling.capture.memory.snapshot.warning.title"),
                               PerformanceTestingBundle.message("profiling.capture.memory.snapshot.warning.content"),
                               NotificationType.WARNING)
    warning.notify(project)

    e.coroutineScope.launch {
      try {
        withBackgroundProgress(owner, PerformanceTestingBundle.message("profiling.capture.memory.snapshot.warning.title"),
                               TaskCancellation.nonCancellable()) {
          delay(PAINT_DELAY)
          LOG.info("Capturing a memory snapshot to $snapshotFile")
          val duration = withContext(Dispatchers.IO) {
            measureTime { MemoryDumpHelper.captureMemoryDump(snapshotFile.absolutePath) }
          }
          LOG.info("Captured the memory snapshot in ${duration.inWholeMilliseconds} ms")
        }
        ProfilerHandlerUtils.notify(project, snapshotFile)
      }
      catch (exception: Exception) {
        rethrowControlFlowException(exception)
        ProfilerHandlerUtils.notifyCapturingError(exception, project)
      }
    }.invokeOnCompletion {
      // also runs when the scope is canceled before the coroutine starts
      warning.expire()
      capturing.set(false)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    val available = MemoryDumpHelper.memoryDumpAvailable()
    e.presentation.isVisible = available
    e.presentation.isEnabled = available && !capturing.get()
  }
}
