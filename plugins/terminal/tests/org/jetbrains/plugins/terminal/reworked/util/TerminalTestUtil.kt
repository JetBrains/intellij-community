// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.reworked.util

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.terminal.session.StyleRange
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModelImpl

internal object TerminalTestUtil {
  fun createOutputModel(maxLength: Int = 0): TerminalOutputModelImpl {
    val document = EditorFactory.getInstance().createDocument("")
    return TerminalOutputModelImpl(document, maxLength)
  }

  suspend fun TerminalOutputModel.update(absoluteLineIndex: Int, text: String, styles: List<StyleRange> = emptyList()) {
    writeAction {
      CommandProcessor.getInstance().runUndoTransparentAction {
        updateContent(absoluteLineIndex, text, styles)
      }
    }
  }

  suspend fun TerminalOutputModel.updateCursor(absoluteLineIndex: Int, column: Int) {
    writeAction {
      CommandProcessor.getInstance().runUndoTransparentAction {
        updateCursorPosition(absoluteLineIndex, column)
      }
    }
  }
}