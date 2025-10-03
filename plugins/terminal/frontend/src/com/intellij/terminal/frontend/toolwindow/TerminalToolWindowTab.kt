package com.intellij.terminal.frontend.toolwindow

import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.ui.content.Content
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalToolWindowTab {
  val view: TerminalView

  val content: Content
}