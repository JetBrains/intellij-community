package com.intellij.terminal.frontend.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.terminal.frontend.TerminalInput
import com.intellij.terminal.frontend.TerminalSearchController

internal object TerminalFrontendDataContextUtils {
  val AnActionEvent.terminalInput: TerminalInput?
    get() = getData(TerminalInput.KEY)

  val DataContext.terminalSearchController: TerminalSearchController?
    get() = getData(TerminalSearchController.KEY)
}