// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.terminal.frontend.view.impl.TerminalOutputScrollingModel
import org.jetbrains.plugins.terminal.block.TerminalPromotedDumbAwareAction
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isOutputModelEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalEditor

internal class TerminalLineUpAction : TerminalScrollingAction(LineUpHandler())

internal class TerminalLineDownAction : TerminalScrollingAction(LineDownHandler())

internal class TerminalPageUpAction : TerminalScrollingAction(PageUpHandler())

internal class TerminalPageDownAction : TerminalScrollingAction(PageDownHandler())

internal interface ScrollingHandler {
  fun doExecute(editor: Editor)
}

internal abstract class TerminalScrollingAction(private val handler: ScrollingHandler) : TerminalPromotedDumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.terminalEditor?.isOutputModelEditor == true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.terminalEditor ?: return
    handler.doExecute(editor)
  }
}

private class PageUpHandler : ScrollingHandlerImpl(Unit.PAGE, -1)

private class PageDownHandler : ScrollingHandlerImpl(Unit.PAGE, +1)

private class LineUpHandler : ScrollingHandlerImpl(Unit.LINE, -1)

private class LineDownHandler : ScrollingHandlerImpl(Unit.LINE, +1)

private abstract class ScrollingHandlerImpl(private val unit: Unit, private val direction: Int) : ScrollingHandler {
  override fun doExecute(editor: Editor) {
    val scrollingModel = editor.getUserData(TerminalOutputScrollingModel.KEY) ?: return
    when (unit) {
      Unit.LINE -> scrollingModel.scrollByLines(direction)
      Unit.PAGE -> scrollingModel.scrollByPages(direction)
    }
  }
}

private enum class Unit {
  LINE,
  PAGE
}
