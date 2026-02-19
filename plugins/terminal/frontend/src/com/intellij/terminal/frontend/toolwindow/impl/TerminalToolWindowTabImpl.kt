package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.ui.content.Content

internal data class TerminalToolWindowTabImpl(
  override val view: TerminalView,
  override val content: Content,
) : TerminalToolWindowTab