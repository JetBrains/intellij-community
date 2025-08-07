// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.terminal.frontend.TerminalSearchController
import com.intellij.terminal.frontend.action.TerminalFrontendDataContextUtils.terminalSearchController
import org.jetbrains.plugins.terminal.block.BlockTerminalController
import org.jetbrains.plugins.terminal.block.TerminalPromotedDumbAwareAction
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.blockTerminalController
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.editor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isAlternateBufferEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isAlternateBufferModelEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isOutputEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isOutputModelEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isPromptEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isReworkedTerminalEditor

internal class TerminalFindAction : TerminalPromotedDumbAwareAction() {
  private val handler = TerminalFindHandler(originalHandler = null)

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT // yes, that's what EditorAction defaults to as well!

  override fun update(e: AnActionEvent) {
    val editor = e.editor
    val caret = editor?.caretModel?.currentCaret
    e.presentation.isEnabled = editor != null && caret != null && handler.isEnabled(editor, caret, e.dataContext)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.editor ?: return
    val caret = editor.caretModel.currentCaret
    handler.executeForTerminal(editor, caret, e.dataContext)
  }
}

internal abstract class TerminalSearchActionHandler(private val originalHandler: EditorActionHandler?) : EditorActionHandler() {

  fun executeForTerminal(editor: Editor, caret: Caret?, dataContext: DataContext) {
    doExecute(editor, caret, dataContext)
  }

  override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
    val blockController = dataContext.blockTerminalController
    val reworkedController = dataContext.terminalSearchController
    if (blockController != null) {
      doWithBlockController(blockController)
    }
    else if (reworkedController != null) {
      doWithReworkedController(editor, reworkedController)
    }
    else originalHandler?.execute(editor, caret, dataContext)
  }

  abstract fun doWithBlockController(blockController: BlockTerminalController)

  abstract fun doWithReworkedController(editor: Editor, searchController: TerminalSearchController)

  override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
    return editor.isPromptEditor
           || editor.isOutputEditor
           || editor.isOutputModelEditor || editor.isAlternateBufferModelEditor
           || originalHandler?.isEnabled(editor, caret, dataContext) == true
  }
}

internal class TerminalFindHandler(originalHandler: EditorActionHandler?) : TerminalSearchActionHandler(originalHandler) {
  override fun doWithBlockController(blockController: BlockTerminalController) {
    if (blockController.searchSession != null) {
      blockController.activateSearchSession()
    }
    else blockController.startSearchSession()
  }

  override fun doWithReworkedController(editor: Editor, searchController: TerminalSearchController) {
    searchController.startOrActivateSearchSession(editor)
  }
}

internal class TerminalFindNextHandler(originalHandler: EditorActionHandler) : TerminalSearchActionHandler(originalHandler) {
  override fun doWithBlockController(blockController: BlockTerminalController) {
    blockController.searchSession?.searchForward()
  }

  override fun doWithReworkedController(editor: Editor, searchController: TerminalSearchController) {
    searchController.searchForward()
  }
}

internal class TerminalFindPreviousHandler(originalHandler: EditorActionHandler) : TerminalSearchActionHandler(originalHandler) {
  override fun doWithBlockController(blockController: BlockTerminalController) {
    blockController.searchSession?.searchBackward()
  }

  override fun doWithReworkedController(editor: Editor, searchController: TerminalSearchController) {
    searchController.searchBackward()
  }
}

/** Do nothing on replace action if it is a terminal editor */
internal class TerminalReplaceHandler(private val originalHandler: EditorActionHandler) : EditorActionHandler() {
  override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
    if (isNotHandledByTerminal(editor)) {
      originalHandler.execute(editor, caret, dataContext)
    }
  }

  override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
    return isNotHandledByTerminal(editor) && originalHandler.isEnabled(editor, caret, dataContext)
  }

  private fun isNotHandledByTerminal(editor: Editor): Boolean =
    !editor.isPromptEditor &&
    !editor.isOutputEditor &&
    !editor.isAlternateBufferEditor &&
    !editor.isReworkedTerminalEditor
}
