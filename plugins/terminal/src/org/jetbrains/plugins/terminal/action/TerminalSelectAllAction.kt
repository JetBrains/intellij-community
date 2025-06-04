// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import org.jetbrains.plugins.terminal.block.TerminalFrontendEditorAction
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isReworkedTerminalEditor

internal class TerminalSelectAllAction : TerminalFrontendEditorAction(SelectAllHandler())

private class SelectAllHandler : EditorActionHandler() {
  override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean = editor.isReworkedTerminalEditor
  override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
    editor.selectionModel.setSelection(0, editor.document.textLength)
  }
}
