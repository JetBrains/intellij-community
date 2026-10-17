// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.vfs.VfsUtilCore
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
import org.jetbrains.annotations.VisibleForTesting

/**
 * The first file that opens after the IDE start, as its [url].
 * The value stays until [WaitForReOpenedFileCommand] takes it.
 * In an IDE that never runs the command, the value stays for the whole process.
 * Thus the value must not keep a [VirtualFile], because a file of a dynamic plugin keeps the plugin class loader alive after an unload.
 */
@VisibleForTesting
internal class ReopenedFile(val span: Span, val url: String)

@VisibleForTesting
internal val reopenedFile: MutableStateFlow<ReopenedFile?> = MutableStateFlow(null)

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
    check(reopened.url == expectedFile.url) {
      "Expected reopened file ${expectedFile.path}, but ${VfsUtilCore.urlToPath(reopened.url)} was reopened"
    }
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
    reopenedFile.compareAndSet(null, ReopenedFile(span, file.url))
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
