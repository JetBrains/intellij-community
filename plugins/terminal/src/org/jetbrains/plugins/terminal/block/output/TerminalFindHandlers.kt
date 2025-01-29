// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.output

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import org.jetbrains.plugins.terminal.block.BlockTerminalController
import org.jetbrains.plugins.terminal.block.reworked.TerminalSearchController
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.blockTerminalController
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isAlternateBufferEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isOutputEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isOutputModelEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isPromptEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isReworkedTerminalEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalSearchController

internal abstract class TerminalSearchActionHandler(private val originalHandler: EditorActionHandler?) : EditorActionHandler() {
  override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
    val blockController = dataContext.blockTerminalController
    val reworkedController = dataContext.terminalSearchController
    if (blockController != null) {
      doWithBlockController(blockController)
    }
    else if (reworkedController != null) {
      doWithReworkedController(reworkedController)
    }
    else originalHandler?.execute(editor, caret, dataContext)
  }

  abstract fun doWithBlockController(blockController: BlockTerminalController)

  abstract fun doWithReworkedController(searchController: TerminalSearchController)

  override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
    return editor.isPromptEditor
           || editor.isOutputEditor
           || editor.isOutputModelEditor
           || originalHandler?.isEnabled(editor, caret, dataContext) == true
  }
}

internal class TerminalFindHandler() : TerminalSearchActionHandler(originalHandler = null) {
  override fun doWithBlockController(blockController: BlockTerminalController) {
    if (blockController.searchSession != null) {
      blockController.activateSearchSession()
    }
    else blockController.startSearchSession()
  }

  override fun doWithReworkedController(searchController: TerminalSearchController) {
    searchController.startOrActivateSearchSession()
  }
}

internal class TerminalFindNextHandler(originalHandler: EditorActionHandler) : TerminalSearchActionHandler(originalHandler) {
  override fun doWithBlockController(blockController: BlockTerminalController) {
    blockController.searchSession?.searchForward()
  }

  override fun doWithReworkedController(searchController: TerminalSearchController) {
    searchController.searchForward()
  }
}

internal class TerminalFindPreviousHandler(originalHandler: EditorActionHandler) : TerminalSearchActionHandler(originalHandler) {
  override fun doWithBlockController(blockController: BlockTerminalController) {
    blockController.searchSession?.searchBackward()
  }

  override fun doWithReworkedController(searchController: TerminalSearchController) {
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
