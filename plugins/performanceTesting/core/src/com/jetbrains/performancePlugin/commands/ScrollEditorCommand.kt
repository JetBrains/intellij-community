// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.jetbrains.performancePlugin.Timer
import kotlinx.coroutines.delay
import org.jetbrains.annotations.NonNls

/**
 * Scroll the current file to the end of the file.
 *
 * Syntax: `%scrollEditor <scroll Delay ms>`
 *
 * Example: `%scrollEditor 100`
 */
class ScrollEditorCommand(text: String, line: Int): PerformanceCommandCoroutineAdapter(text, line) {

  companion object {
    const val NAME: @NonNls String = "scrollEditor"
    const val PREFIX: @NonNls String = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val scrollDelay = extractCommandArgument(PREFIX).toLongOrNull() ?: 100
    
    val timer = Timer()
    timer.start(NAME, true)

    val editor = edtWriteAction { checkNotNull(FileEditorManager.getInstance(context.project).selectedTextEditor) }
    val totalLines = readAction { editor.document.lineCount } - 1

    var lineToScrollTo = 0
    while (lineToScrollTo <= totalLines) {
      edtWriteAction {
        val logicalPosition = editor.visualToLogicalPosition(VisualPosition(lineToScrollTo, 0))
        editor.scrollingModel.scrollTo(logicalPosition, ScrollType.RELATIVE)
      }
      lineToScrollTo += 5
      delay(scrollDelay)
    }
    timer.stop()
  }

  override fun getName(): String {
    return NAME
  }
}
