package com.intellij.terminal.frontend.toolwindow

import com.intellij.terminal.TerminalTitle
import com.intellij.terminal.frontend.TerminalView
import com.intellij.ui.content.Content
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalToolWindowTab {
  val view: TerminalView

  val title: TerminalTitle

  val content: Content
}