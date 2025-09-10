// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.performancePlugin.PerformanceTestSpan
import com.jetbrains.performancePlugin.utils.HighlightingTestUtil
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import kotlinx.coroutines.sync.Mutex
import org.jetbrains.annotations.NonNls

class WaitForReOpenedFileCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {

  companion object {
    const val NAME: @NonNls String = "waitForReOpenedFile"
    const val PREFIX: @NonNls String = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val myOptions = OpenFileCommand.getOptions(extractCommandArgument(PREFIX))
    val fileName = myOptions!!.file.toNioPathOrNull()!!.fileName.toString()
    val connection = context.project.messageBus.connect()
    var span: Span? = null

    val fileOpenedLock = Mutex(true)
    connection.subscribe(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER, object : FileEditorManagerListener.Before {
      override fun beforeFileOpened(source: FileEditorManager, file: VirtualFile) {
        logger<WaitForReOpenedFileCommand>().info("beforeFileOpened ${file.name}")
        if (file.name != fileName) return
        span = startSpan("reopenFileAfterIdeRestart", null, "filePath" to myOptions.file)
        fileOpenedLock.unlock()
      }
    })

    fileOpenedLock.lock()

    HighlightingTestUtil.waitForAnalysisWithNewApproach(context.project, span, myOptions.timeout, myOptions.suppressErrors)
    connection.disconnect()
  }

  private fun startSpan(spanName: String, parentSpan: Span?, attribute: Pair<String, String>? = null): Span {
    val builder = PerformanceTestSpan.TRACER.spanBuilder(spanName)
      .setParent(if (parentSpan == null) PerformanceTestSpan.getContext() else Context.current().with(parentSpan))

    if (attribute != null) builder.setAttribute(attribute.first, attribute.second)
    return builder.startSpan()
  }

  override fun getName(): String = NAME

}