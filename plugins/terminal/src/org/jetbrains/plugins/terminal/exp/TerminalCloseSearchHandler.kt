// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.blockTerminalController
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.isOutputEditor
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.isPromptEditor

class TerminalCloseSearchHandler(private val originalHandler: EditorActionHandler) : EditorActionHandler() {
  override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
    val blockController = dataContext.blockTerminalController
    if (blockController?.searchSession != null) {
      blockController.finishSearchSession()
    }
    else originalHandler.execute(editor, caret, dataContext)
  }

  override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext): Boolean {
    return (editor.isPromptEditor || editor.isOutputEditor) && dataContext.blockTerminalController?.searchSession != null
           || originalHandler.isEnabled(editor, caret, dataContext)
  }
}