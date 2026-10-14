// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.jetbrains.performancePlugin.PerformanceTestingBundle
import com.jetbrains.performancePlugin.utils.HighlightingTestUtil
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.jetbrains.annotations.NonNls

private class ReopenedFile(val span: Span, val file: VirtualFile)
private val reopenedFile = MutableStateFlow<ReopenedFile?>(null)

open class WaitForReOpenedFileCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {

  companion object {
    const val NAME: @NonNls String = "waitForReOpenedFile"
    const val PREFIX: @NonNls String = "$CMD_PREFIX$NAME"
  }
  override suspend fun doExecute(context: PlaybackContext) {
    val myOptions = OpenFileCommand.getOptions(extractCommandArgument(PREFIX))
    val filePath = myOptions!!.file.replace("SPACE_SYMBOL", " ")
    val expectedFile = OpenFileCommand.findFile(filePath, context.project)
                       ?: error(PerformanceTestingBundle.message("command.file.not.found", filePath))
    val reopened = reopenedFile.filterNotNull().first()
    reopenedFile.compareAndSet(reopened, null)
    check(reopened.file == expectedFile) { "Expected reopened file ${expectedFile.path}, but ${reopened.file.path} was reopened" }
    HighlightingTestUtil.waitForAnalysisWithNewApproach(context.project, reopened.span, myOptions.timeout, myOptions.suppressErrors)
  }

  override fun getName(): String = NAME

}

internal class BeforeFileOpenLoggerListener : FileEditorManagerListener.Before {
  override fun beforeFileOpened(source: FileEditorManager, file: VirtualFile) {
    logger<BeforeFileOpenLoggerListener>().info("beforeFileOpened ${file.name}")
    if (reopenedFile.value != null) return
    val span = TelemetryManager.getTracer(Scope("beforeFileOpened"))
      .spanBuilder("reopenFileAfterIdeRestart")
      .setNoParent()
      .startSpan()
    reopenedFile.compareAndSet(null, ReopenedFile(span, file))
  }
}

internal class FileOpenLoggerListener : FileEditorManagerListener {
  override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
    logger<BeforeFileOpenLoggerListener>().info("fileOpened ${file.name}")
  }

  override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
    logger<BeforeFileOpenLoggerListener>().info("fileClosed ${file.name}")
  }

}
