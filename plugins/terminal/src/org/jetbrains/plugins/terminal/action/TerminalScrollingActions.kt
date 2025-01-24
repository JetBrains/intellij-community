// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actions.EditorActionUtil
import org.jetbrains.plugins.terminal.block.TerminalPromotedEditorAction
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isOutputModelEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isReworkedTerminalEditor

internal class TerminalLineUpAction : TerminalPromotedEditorAction(LineUpHandler())

internal class TerminalLineDownAction : TerminalPromotedEditorAction(LineDownHandler())

internal class TerminalPageUpAction : TerminalPromotedEditorAction(PageUpHandler())

internal class TerminalPageDownAction : TerminalPromotedEditorAction(PageDownHandler())

private class PageUpHandler : Handler(Unit.PAGE, -1)

private class PageDownHandler : Handler(Unit.PAGE, +1)

private class LineUpHandler : Handler(Unit.LINE, -1)

private class LineDownHandler : Handler(Unit.LINE, +1)

private abstract class Handler(private val unit: Unit, private val direction: Int) : EditorActionHandler() {
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
