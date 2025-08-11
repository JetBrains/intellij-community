// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.prompt

import com.intellij.codeInsight.editorActions.BaseEnterHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isPromptEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.promptController
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalSession
import org.jetbrains.plugins.terminal.exp.completion.TerminalShellSupport

internal class TerminalEnterHandler(private val originalHandler: EditorActionHandler) : BaseEnterHandler() {
  override fun executeWriteAction(editor: Editor, caret: Caret?, dataContext: DataContext) {
    val promptController = dataContext.promptController
    val session = dataContext.terminalSession
    if (promptController != null && session != null) {
      val offset = editor.caretModel.offset
      val shellSupport = TerminalShellSupport.findByShellType(session.shellIntegration.shellType)
      if (offset == promptController.model.commandStartOffset
          || shellSupport == null
          || editor.document.getText(TextRange(offset - 1, offset)) != shellSupport.lineContinuationChar.toString()) {
        promptController.handleEnterPressed()
        return
      }
    }
    originalHandler.execute(editor, caret, dataContext)
  }

  override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext): Boolean {
    return dataContext.terminalEditor?.isPromptEditor == true || originalHandler.isEnabled(editor, caret, dataContext)
  }
}
