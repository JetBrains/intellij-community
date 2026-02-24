package com.intellij.terminal.frontend.toolwindow

import com.intellij.ui.content.ContentManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo
import org.jetbrains.plugins.terminal.startup.TerminalProcessType

/**
 * Builder for creating a new [TerminalToolWindowTab].
 * Use [createTab] to finish building and actually create the tab.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalToolWindowTabBuilder {
  /**
   * Specifies the absolute OS-dependent path to the directory where the shell process should be started.
   *
   * If not specified, the working directory configured in the Terminal settings will be used.
   * ([org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider.startingDirectory])
   */
  fun workingDirectory(directory: String?): TerminalToolWindowTabBuilder

  /**
   * Specifies the command that should be used to start the shell process.
   * For example [/bin/zsh, -i, --login].
   * The list should not be empty.
   *
   * If not specified, the shell path configured in the Terminal settings will be used.
   * ([org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider.shellPath])
   */
  fun shellCommand(command: List<String>?): TerminalToolWindowTabBuilder

  /**
   * Specifies the type of the process that should be started in the terminal.
   * It directly affects what base set of environment variables is used to start the process.
   *
   * If not specified, the default value is [TerminalProcessType.SHELL].
   * Specify [TerminalProcessType.NON_SHELL] if you start some arbitrary PTY process that is not a shell.
   */
  fun processType(processType: TerminalProcessType): TerminalToolWindowTabBuilder

  /**
   * The title show in the tool window tab.
   *
   * If not specified, the default tab name specified in the Terminal settings will be used.
   * ([org.jetbrains.plugins.terminal.TerminalOptionsProvider.defaultTabName])
   */
  fun tabName(name: String?): TerminalToolWindowTabBuilder

  /**
   * Whether to move focus to the terminal tab after it opens.
   * True by default.
   */
  fun requestFocus(requestFocus: Boolean): TerminalToolWindowTabBuilder

  /**
   * Whether to wait until the UI is shown before starting the shell process.
   * True by default.
   *
   * When `true`, it ensures that the process is started with the correct terminal grid size.
   * Otherwise, the process will be started with the default terminal grid size.
   */
  fun deferSessionStartUntilUiShown(defer: Boolean): TerminalToolWindowTabBuilder

  /**
   * Allows specifying the exact split area of the Terminal Tool Window where the tab should be opened.
   * If it is `null`, the tab will be opened in the top-left split area (or in the main area if there are no splits).
   */
  fun contentManager(manager: ContentManager?): TerminalToolWindowTabBuilder

  /**
   * Whether to add the tab to the Terminal tool window or create the detached tab.
   * True by default.
   *
   * Internal for now, because most probably it will be removed or reworked.
   */
  @ApiStatus.Internal
  fun shouldAddToToolWindow(addToToolWindow: Boolean): TerminalToolWindowTabBuilder

  @ApiStatus.Internal
  fun startupFusInfo(startupFusInfo: TerminalStartupFusInfo?): TerminalToolWindowTabBuilder

  /**
   * Creates the new Reworked Terminal tab and adds it to the Terminal tool window.
   * Starts the shell process according to the [deferSessionStartUntilUiShown] option.
   */
  @RequiresEdt
  fun createTab(): TerminalToolWindowTab
}