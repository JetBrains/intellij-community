package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.terminal.TerminalTitle
import com.intellij.terminal.frontend.TerminalView
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab
import com.intellij.ui.content.Content

internal data class TerminalToolWindowTabImpl(
  override val view: TerminalView,
  override val title: TerminalTitle,
  override val content: Content,
) : TerminalToolWindowTab