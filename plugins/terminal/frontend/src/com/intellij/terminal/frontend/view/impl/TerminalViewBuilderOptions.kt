package com.intellij.terminal.frontend.view.impl

import com.intellij.terminal.frontend.toolwindow.TerminalRequestedProcessOptions
import org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo

internal data class TerminalViewBuilderOptions(
  val processOptions: TerminalRequestedProcessOptions,
  val deferSessionStartUntilUiShown: Boolean = true,
  val startupFusInfo: TerminalStartupFusInfo? = null,
)