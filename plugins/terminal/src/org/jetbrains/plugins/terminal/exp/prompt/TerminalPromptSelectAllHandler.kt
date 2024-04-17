// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.prompt

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.isPromptEditor
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.terminalPromptModel

class TerminalPromptSelectAllHandler(private val originalHandler: EditorActionHandler) : EditorActionHandler() {
  override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
    val promptModel = editor.terminalPromptModel
    if (editor.isPromptEditor && promptModel != null) {
      executeCommand(CommonDataKeys.PROJECT.getData(dataContext), IdeBundle.message("command.select.all")) {
        editor.selectionModel.setSelection(promptModel.commandStartOffset, editor.document.textLength)
      }
    }
    else originalHandler.execute(editor, caret, dataContext)
  }

  override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
    return editor.isPromptEditor || originalHandler.isEnabled(editor, caret, dataContext)
  }
}