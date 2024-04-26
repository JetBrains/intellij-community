// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.history

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.ui.ScrollingUtil
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.isPromptEditor
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.promptController
import org.jetbrains.plugins.terminal.exp.history.CommandSearchPresenter.Companion.isTerminalCommandSearch
import org.jetbrains.plugins.terminal.exp.TerminalPromotedEditorAction

internal class CommandSearchAction : TerminalPromotedEditorAction(Handler()), ActionRemoteBehaviorSpecification.Disabled {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  private class Handler : EditorActionHandler() {
    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
      LookupManager.getActiveLookup(editor)?.hideLookup(true)
      dataContext.promptController?.showCommandSearch()
    }

    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
      return editor.isPromptEditor
    }
  }
}

internal class CommandSearchSelectNextHandler(private val originalHandler: EditorActionHandler) : EditorActionHandler() {
  override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
    val lookup = LookupManager.getActiveLookup(editor) as? LookupImpl
    if (lookup?.isTerminalCommandSearch == true) {
      ScrollingUtil.moveUp(lookup.list, 0)
      lookup.markSelectionTouched()
      lookup.refreshUi(false, true)
    }
    else originalHandler.execute(editor, caret, dataContext)
  }

  override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
    return editor.isPromptEditor && LookupManager.getActiveLookup(editor)?.isTerminalCommandSearch == true
           || originalHandler.isEnabled(editor, caret, dataContext)
  }
}