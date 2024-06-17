// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.output

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import org.jetbrains.plugins.terminal.block.BlockTerminalController
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.blockTerminalController
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isAlternateBufferEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isOutputEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isPromptEditor

internal abstract class TerminalSearchActionHandler(private val originalHandler: EditorActionHandler) : EditorActionHandler() {
  override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
    val blockController = dataContext.blockTerminalController
    if (blockController != null) {
      doWithBlockController(blockController)
    }
    else originalHandler.execute(editor, caret, dataContext)
  }

  abstract fun doWithBlockController(blockController: BlockTerminalController)

  override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
    return editor.isPromptEditor
           || editor.isOutputEditor
           || originalHandler.isEnabled(editor, caret, dataContext)
  }
}

internal class TerminalFindHandler(originalHandler: EditorActionHandler) : TerminalSearchActionHandler(originalHandler) {
  override fun doWithBlockController(blockController: BlockTerminalController) {
    if (blockController.searchSession != null) {
      blockController.activateSearchSession()
    }
    else blockController.startSearchSession()
  }
}

internal class TerminalFindNextHandler(originalHandler: EditorActionHandler) : TerminalSearchActionHandler(originalHandler) {
  override fun doWithBlockController(blockController: BlockTerminalController) {
    blockController.searchSession?.searchForward()
  }
}

internal class TerminalFindPreviousHandler(originalHandler: EditorActionHandler) : TerminalSearchActionHandler(originalHandler) {
  override fun doWithBlockController(blockController: BlockTerminalController) {
    blockController.searchSession?.searchBackward()
  }
}

/** Do nothing on replace action if it is a terminal editor */
internal class TerminalReplaceHandler(private val originalHandler: EditorActionHandler) : EditorActionHandler() {
  override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
    if (!editor.isPromptEditor && !editor.isOutputEditor && !editor.isAlternateBufferEditor) {
      originalHandler.execute(editor, caret, dataContext)
    }
  }

  override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
    return !editor.isPromptEditor
           && !editor.isOutputEditor
           && !editor.isAlternateBufferEditor
           && originalHandler.isEnabled(editor, caret, dataContext)
  }
}
