// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actions.EditorActionUtil
import org.jetbrains.plugins.terminal.block.TerminalFrontendEditorAction
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isOutputModelEditor

internal class TerminalLineUpAction : TerminalFrontendEditorAction(LineUpHandler())

internal class TerminalLineDownAction : TerminalFrontendEditorAction(LineDownHandler())

internal class TerminalPageUpAction : TerminalFrontendEditorAction(PageUpHandler())

internal class TerminalPageDownAction : TerminalFrontendEditorAction(PageDownHandler())

private class PageUpHandler : ScrollingHandler(Unit.PAGE, -1)

private class PageDownHandler : ScrollingHandler(Unit.PAGE, +1)

private class LineUpHandler : ScrollingHandler(Unit.LINE, -1)

private class LineDownHandler : ScrollingHandler(Unit.LINE, +1)

private abstract class ScrollingHandler(private val unit: Unit, private val direction: Int) : EditorActionHandler() {
  override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
    return editor.isOutputModelEditor
  }

  override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
    val amount = when (unit) {
      Unit.LINE -> 1
      Unit.PAGE -> editor.scrollingModel.visibleArea.height / editor.lineHeight
    }
    EditorActionUtil.scrollRelatively(editor, amount * direction, 0, false)
  }
}

private enum class Unit {
  LINE,
  PAGE
}
