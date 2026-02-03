package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.frontend.FrontendApplicationInfo
import com.intellij.frontend.FrontendType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.content.ContentManager
import org.jetbrains.plugins.terminal.TerminalEngine
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.TerminalTabState
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo

/**
 * Creates the new terminal tab in the Terminal Tool Window.
 * Actual [TerminalEngine] of the created tab depends on the user settings and known restrictions.
 */
internal fun createTerminalTab(
  project: Project,
  shellCommand: List<String>? = null,
  workingDirectory: String? = null,
  @NlsSafe tabName: String? = null,
  contentManager: ContentManager? = null,
  startupFusInfo: TerminalStartupFusInfo? = null,
) {
  if (shouldUseReworkedTerminal()) {
    TerminalToolWindowTabsManager.getInstance(project).createTabBuilder()
      .shellCommand(shellCommand)
      .workingDirectory(workingDirectory)
      .tabName(tabName)
      .contentManager(contentManager)
      .startupFusInfo(startupFusInfo)
      .createTab()
  }
  else {
    // Otherwise, create the Classic or Gen1 terminal tab using old API.
    val tabState = TerminalTabState().also {
      it.myShellCommand = shellCommand
      it.myWorkingDirectory = workingDirectory
      it.myTabName = tabName
    }
    val engine = TerminalOptionsProvider.instance.terminalEngine
    TerminalToolWindowManager.getInstance(project).createNewTab(engine, tabState, contentManager)
  }
}

/**
 * Checks for user settings and known restrictions and returns true if the Reworked Terminal should be used.
 */
internal fun shouldUseReworkedTerminal(): Boolean {
  val engine = TerminalOptionsProvider.instance.terminalEngine
  val frontendType = FrontendApplicationInfo.getFrontendType()
  val isCodeWithMe = frontendType is FrontendType.Remote && frontendType.isGuest()
  return ExperimentalUI.isNewUI() && engine == TerminalEngine.REWORKED && !isCodeWithMe
}