// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actions.EditorActionUtil
import org.jetbrains.plugins.terminal.block.TerminalPromotedEditorAction

internal class TerminalPageUpAction : TerminalPromotedEditorAction(PageUpHandler())

internal class TerminalPageDownAction : TerminalPromotedEditorAction(PageDownHandler())

private class PageUpHandler : Handler(-1)

private class PageDownHandler : Handler(+1)

private abstract class Handler(private val direction: Int) : EditorActionHandler() {
  override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
    val pageLines = editor.scrollingModel.visibleArea.height / editor.lineHeight
    EditorActionUtil.scrollRelatively(editor, pageLines * direction, 0, false)
  }
}
