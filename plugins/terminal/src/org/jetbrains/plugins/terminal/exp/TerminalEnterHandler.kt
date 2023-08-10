// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.codeInsight.editorActions.BaseEnterHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.TextRange

class TerminalEnterHandler(private val originalHandler: EditorActionHandler) : BaseEnterHandler() {
  override fun executeWriteAction(editor: Editor, caret: Caret?, dataContext: DataContext?) {
    val promptPanel = editor.getUserData(TerminalPromptPanel.KEY)
    if (promptPanel != null) {
      val offset = editor.caretModel.offset
      if (offset == 0 || editor.document.getText(TextRange(offset - 1, offset)) != "\\") {
        promptPanel.handleEnterPressed()
        return
      }
    }
    originalHandler.execute(editor, caret, dataContext)
  }

  override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
    return editor.getUserData(TerminalPromptPanel.KEY) != null || originalHandler.isEnabled(editor, caret, dataContext)
  }
}