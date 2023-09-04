// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.UserDataHolder
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.isPromptEditor
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.promptController


class TerminalCaretUpHandler(private val originalHandler: EditorActionHandler) : EditorActionHandler() {
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

class TerminalCaretDownHandler(private val originalHandler: EditorActionHandler) : EditorActionHandler() {
  override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
    val promptController = dataContext.promptController
    if (promptController != null) {
      val lookup = LookupManager.getActiveLookup(editor) as? LookupImpl
      if (lookup != null && lookup.isAvailableToUser
          && lookup.getUserData(CommandHistoryPresenter.IS_COMMAND_HISTORY_LOOKUP_KEY) == true) {
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


class TerminalCloseHistoryHandler(private val originalHandler: EditorActionHandler) : EditorActionHandler() {
  override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
    val promptController = dataContext.promptController
    if (promptController != null) {
      val lookup = LookupManager.getActiveLookup(editor) as? UserDataHolder
      if (lookup?.getUserData(CommandHistoryPresenter.IS_COMMAND_HISTORY_LOOKUP_KEY) == true) {
        promptController.onCommandHistoryClosed()
        return
      }
    }
    originalHandler.execute(editor, caret, dataContext)
  }

  override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
    val lookup = LookupManager.getActiveLookup(editor) as? UserDataHolder
    return editor.isPromptEditor && lookup?.getUserData(CommandHistoryPresenter.IS_COMMAND_HISTORY_LOOKUP_KEY) == true
           || originalHandler.isEnabled(editor, caret, dataContext)
  }
}