// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.performancePlugin.utils.HighlightingTestUtil
import org.jetbrains.annotations.NonNls

open class WaitForReOpenedFileCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {

  companion object {
    const val NAME: @NonNls String = "waitForReOpenedFile"
    const val PREFIX: @NonNls String = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val myOptions = OpenFileCommand.getOptions(extractCommandArgument(PREFIX))
    HighlightingTestUtil.waitForAnalysisWithNewApproach(context.project, null, myOptions!!.timeout, myOptions.suppressErrors)
  }

  override fun getName(): String = NAME

}

internal class BeforeFileOpenLoggerListener : FileEditorManagerListener.Before {
  override fun beforeFileOpened(source: FileEditorManager, file: VirtualFile) {
    logger<BeforeFileOpenLoggerListener>().info("beforeFileOpened ${file.name}")
    HighlightingTestUtil.storeProcessFinishedTime("beforeFileOpened", "reopenFileAfterIdeRestart")
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