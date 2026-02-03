// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.playback.PlaybackContext
import org.jetbrains.annotations.NonNls

/**
 * Assert that the caret is located at the specified position.
 * Lines and columns are counted from 1.
 *
 * Syntax: `%assertCaretPosition <line> <column>`
 *
 * Example: `%assertCaretPosition 5 16`
 */
class AssertCaretPositionCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME: @NonNls String = "assertCaretPosition"
    const val PREFIX: String = CMD_PREFIX + NAME
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val (line, column) = extractCommandArgument(PREFIX).split(" ").filterNot { it.trim() == "" }.map { it.toInt() }
    if (line < 0 || column < 0) {
      throw IllegalArgumentException("Line $line and column $column must be non-negative")
    }

    readAction {
      val editor = checkNotNull(FileEditorManager.getInstance(context.project).selectedTextEditor)
      val document = editor.document
      if (line > document.lineCount) {
        throw Exception("Line $line must be greater than document.lineCount ($document.lineCount)")
      }
      val offset = editor.caretModel.offset
      val currentLineNumber = document.getLineNumber(offset) + 1
      if (line != currentLineNumber) {
        throw Exception("Caret at at line $currentLineNumber, expected $line")
      }
      val currentColumn = offset - document.getLineStartOffset(line - 1) + 1
      if (currentColumn != column) {
        throw Exception("Caret at column $currentColumn, expected $column")
      }
    }
  }

  override fun getName(): String = NAME
}