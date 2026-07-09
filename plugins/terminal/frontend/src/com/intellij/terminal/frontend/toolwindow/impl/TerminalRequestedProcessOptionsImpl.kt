package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.terminal.frontend.toolwindow.TerminalRequestedProcessOptions
import org.jetbrains.plugins.terminal.startup.TerminalProcessType

internal data class TerminalRequestedProcessOptionsImpl(
  override val shellCommand: List<String>?,
  override val workingDirectory: String?,
  override val envVariables: Map<String, String>,
  override val processType: TerminalProcessType,
) : TerminalRequestedProcessOptions