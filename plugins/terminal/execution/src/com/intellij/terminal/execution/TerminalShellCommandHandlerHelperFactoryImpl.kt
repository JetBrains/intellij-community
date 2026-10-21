package com.intellij.terminal.execution

import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalShellCommandHandlerHelper
import org.jetbrains.plugins.terminal.TerminalShellCommandHandlerHelperImpl
import java.awt.event.KeyEvent

internal class TerminalShellCommandHandlerHelperFactoryImpl : TerminalShellCommandHandlerHelper.Factory {
  override val isEnabled: Boolean
    get() = TerminalShellCommandHandlerHelperImpl.isFeatureEnabled()

  override fun createInstance(widget: ShellTerminalWidget): TerminalShellCommandHandlerHelper {
    return TerminalShellCommandHandlerHelperImpl(widget)
  }

  override fun canProcessCustomKeyEvent(keyEvent: KeyEvent): Boolean {
    return TerminalShellCommandHandlerHelperImpl.matchedExecutor(keyEvent) != null
  }
}
