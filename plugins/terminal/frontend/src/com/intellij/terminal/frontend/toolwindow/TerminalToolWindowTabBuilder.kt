package com.intellij.terminal.frontend.toolwindow

import com.intellij.ui.content.ContentManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalToolWindowTabBuilder {
  fun workingDirectory(directory: String?): TerminalToolWindowTabBuilder

  fun shellCommand(command: List<String>?): TerminalToolWindowTabBuilder

  fun tabName(name: String?): TerminalToolWindowTabBuilder

  fun requestFocus(requestFocus: Boolean): TerminalToolWindowTabBuilder

  fun deferSessionStartUntilUiShown(defer: Boolean): TerminalToolWindowTabBuilder

  fun contentManager(manager: ContentManager?): TerminalToolWindowTabBuilder

  /**
   * Whether to add the tab to the Terminal tool window or create the detached tab.
   * True by default.
   */
  fun shouldAddToToolWindow(addToToolWindow: Boolean): TerminalToolWindowTabBuilder

  @ApiStatus.Internal
  fun startupFusInfo(startupFusInfo: TerminalStartupFusInfo?): TerminalToolWindowTabBuilder

  @RequiresEdt
  fun createTab(): TerminalToolWindowTab
}