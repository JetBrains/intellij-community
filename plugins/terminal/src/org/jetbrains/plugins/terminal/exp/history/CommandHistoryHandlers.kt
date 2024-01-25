// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.history

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.isPromptEditor
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.promptController
import org.jetbrains.plugins.terminal.exp.history.CommandHistoryPresenter.Companion.isTerminalCommandHistory


internal class TerminalCaretUpHandler(private val originalHandler: EditorActionHandler) : EditorActionHandler() {
  override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
    val promptController = dataContext.promptController
    if (promptController != null) {
      val lookup = LookupManager.getActiveLookup(editor) as? LookupImpl
      if (lookup?.isAvailableToUser == true) {
        originalHandler.execute(editor, caret, dataContext)
      }
      else if (editor.offsetToLogicalPosition(editor.caretModel.offset).line == 0) {
        promptController.showCommandHistory()
      }
      else originalHandler.execute(editor, caret, dataContext)
    }
    else originalHandler.execute(editor, caret, dataContext)
  }

  override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
    return editor.isPromptEditor || originalHandler.isEnabled(editor, caret, dataContext)
  }
}

internal class TerminalCaretDownHandler(private val originalHandler: EditorActionHandler) : EditorActionHandler() {
  override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
    val promptController = dataContext.promptController
    if (promptController != null) {
      val lookup = LookupManager.getActiveLookup(editor) as? LookupImpl
      if (lookup != null && lookup.isAvailableToUser && lookup.isTerminalCommandHistory) {
        if (lookup.selectedIndex == lookup.list.model.size - 1) {
          promptController.onCommandHistoryClosed()
          lookup.hideLookup(true)
          return
        }
      }
    }
    originalHandler.execute(editor, caret, dataContext)
  }

  override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
    return editor.isPromptEditor || originalHandler.isEnabled(editor, caret, dataContext)
  }
}