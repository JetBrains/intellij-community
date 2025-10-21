// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.block.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.command.impl.UndoManagerImpl
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.TextRange
import com.intellij.util.DocumentUtil
import org.jetbrains.plugins.terminal.block.output.EmptyTextAttributesProvider
import org.jetbrains.plugins.terminal.block.output.HighlightingInfo
import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptModel
import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptRenderingInfo
import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptState
import org.jetbrains.plugins.terminal.block.prompt.error.TerminalPromptErrorDescription
import org.jetbrains.plugins.terminal.block.prompt.error.TerminalPromptErrorStateListener

internal class TestTerminalPromptModel(override val editor: EditorEx, promptText: String = "") : TerminalPromptModel {
  private val document: Document
    get() = editor.document

  override val promptState: TerminalPromptState = TerminalPromptState(currentDirectory = promptText)

  override val renderingInfo: TerminalPromptRenderingInfo = TerminalPromptRenderingInfo(
    promptText,
    listOf(HighlightingInfo(0, promptText.length, EmptyTextAttributesProvider))
  )

  override val commandStartOffset: Int = promptText.length

  override var commandText: String
    get() = document.getText(TextRange(commandStartOffset, document.textLength))
    set(value) {
      DocumentUtil.writeInRunUndoTransparentAction {
        document.replaceString(commandStartOffset, document.textLength, value)
      }
    }

  override fun resetChangesHistory() {
    val undoManager = UndoManager.getInstance(editor.project!!) as UndoManagerImpl
    undoManager.invalidateActionsFor(DocumentReferenceManager.getInstance().create(document))
  }

  override fun setErrorDescription(errorDescription: TerminalPromptErrorDescription?) {
    throw UnsupportedOperationException()
  }

  override fun addErrorStateListener(listener: TerminalPromptErrorStateListener, parentDisposable: Disposable) {
    throw UnsupportedOperationException()
  }

  override fun dispose() {}
}