package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.frontend.FrontendApplicationInfo
import com.intellij.frontend.FrontendType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.terminal.frontend.toolwindow.getTerminalTab
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.content.ContentManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.execution.ParametersListUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.TerminalEngine
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.TerminalTabState
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector
import org.jetbrains.plugins.terminal.fus.TerminalCommandUsageStatistics
import org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo
import org.jetbrains.plugins.terminal.startup.TerminalProcessType
import org.jetbrains.plugins.terminal.util.getNow
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandBlock

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
@ApiStatus.Internal
fun shouldUseReworkedTerminal(): Boolean {
  val engine = TerminalOptionsProvider.instance.terminalEngine
  val frontendType = FrontendApplicationInfo.getFrontendType()
  val isCodeWithMe = frontendType is FrontendType.Remote && frontendType.isGuest()
  return ExperimentalUI.isNewUI() && engine == TerminalEngine.REWORKED && !isCodeWithMe
}

/**
 * Returns the executable of the running process in the form that FUS accepts.
 * Returns null if the shell runs no command.
 * Returns [TerminalCommandUsageStatistics.UNKNOWN_EXECUTABLE] if the process is not started yet,
 * or the shell integration is not available.
 */
internal fun TerminalView.getRunningProcessExecutableForFus(): String? {
  val startupOptions = startupOptionsDeferred.getNow() ?: return TerminalCommandUsageStatistics.UNKNOWN_EXECUTABLE
  val commandLine = if (startupOptions.processType == TerminalProcessType.NON_SHELL) {
    ParametersListUtil.join(startupOptions.shellCommand)
  }
  else {
    // If it is a shell process, we need to get the current running command via shell integration
    val shellIntegration = shellIntegrationDeferred.getNow() ?: return TerminalCommandUsageStatistics.UNKNOWN_EXECUTABLE
    val currentBlock = shellIntegration.blocksModel.activeBlock as? TerminalCommandBlock ?: return null
    currentBlock.executedCommand ?: return null
  }
  return TerminalCommandUsageStatistics.getLoggableCommandData(commandLine, expandAbsoluteOrRelativePath = true).command
}

@RequiresEdt
internal fun ContentManager.getTerminalTabs(): List<TerminalToolWindowTab> {
  return contentsRecursively.mapNotNull { it.getTerminalTab() }
}
