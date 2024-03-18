// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.isOutputEditor
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.isPromptEditor
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.terminalFocusModel

/**
 * Implement this action handler for prompt modification actions.
 * If the action is invoked in the prompt editor, the action will be performed.
 * If the action is invoked in the output editor, the focus will be moved to prompt instead of performing an action.
 */
abstract class TerminalPromptEditorActionHandler(runForEachCaret: Boolean = false) : EditorActionHandler(runForEachCaret) {
  final override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
    if (editor.isOutputEditor) {
      dataContext.terminalFocusModel?.focusPrompt()
    }
    else if (editor.isPromptEditor) {
      executeAction(editor, caret, dataContext)
    }
  }

  abstract fun executeAction(editor: Editor, caret: Caret?, dataContext: DataContext)

  override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext): Boolean {
    return editor.isPromptEditor || editor.isOutputEditor
  }
}