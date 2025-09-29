package com.intellij.terminal.frontend

import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab
import com.intellij.ui.content.ContentManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalTabBuilder {
  fun workingDirectory(directory: String?): TerminalTabBuilder

  fun shellCommand(command: List<String>?): TerminalTabBuilder

  fun tabName(name: String?): TerminalTabBuilder

  fun requestFocus(requestFocus: Boolean): TerminalTabBuilder

  fun deferSessionStartUntilUiShown(defer: Boolean): TerminalTabBuilder

  fun contentManager(manager: ContentManager?): TerminalTabBuilder

  @RequiresEdt
  fun createTab(): TerminalToolWindowTab
}