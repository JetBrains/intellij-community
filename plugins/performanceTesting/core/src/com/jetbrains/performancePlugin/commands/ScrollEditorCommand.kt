package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.jetbrains.performancePlugin.Timer
import kotlinx.coroutines.delay
import org.jetbrains.annotations.NonNls

class ScrollEditorCommand(text: String, line: Int): PerformanceCommandCoroutineAdapter(text, line) {

  companion object {
    const val NAME: @NonNls String = "scrollEditor"
    const val PREFIX: @NonNls String = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val timer = Timer()
    timer.start(NAME, true)

    val editor = writeAction {
      checkNotNull(FileEditorManager.getInstance(context.project).selectedTextEditor).also {
        it.caretModel.currentCaret.moveToLogicalPosition(LogicalPosition(0, 0))
      }
    }

    val totalLines = readAction { editor.document.lineCount } - 1
    while (true) {
      writeAction { editor.caretModel.moveCaretRelatively(0, 5, false, false, true) }
      val currentLine = readAction { editor.caretModel.logicalPosition.line }
      if (currentLine == totalLines) break
      delay(100)
    }
    timer.stop()
  }

  override fun getName(): String {
    return NAME
  }
}
