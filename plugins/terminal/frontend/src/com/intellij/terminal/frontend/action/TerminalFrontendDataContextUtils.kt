package com.intellij.terminal.frontend.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.terminal.frontend.TerminalInput

internal object TerminalFrontendDataContextUtils {
  val AnActionEvent.terminalInput: TerminalInput?
    get() = getData(TerminalInput.KEY)
}