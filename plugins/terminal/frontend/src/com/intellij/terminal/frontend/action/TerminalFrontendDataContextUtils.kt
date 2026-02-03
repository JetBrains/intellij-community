package com.intellij.terminal.frontend.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.impl.TerminalInput
import com.intellij.terminal.frontend.view.impl.TerminalSearchController
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.view.TerminalOutputModel

@ApiStatus.Experimental
object TerminalFrontendDataContextUtils {
  val AnActionEvent.terminalView: TerminalView?
    get() = getData(TerminalView.DATA_KEY)

  internal val AnActionEvent.terminalInput: TerminalInput?
    get() = getData(TerminalInput.DATA_KEY)

  internal val AnActionEvent.terminalOutputModel: TerminalOutputModel?
    get() = getData(TerminalOutputModel.DATA_KEY)

  internal val DataContext.terminalSearchController: TerminalSearchController?
    get() = getData(TerminalSearchController.KEY)
}