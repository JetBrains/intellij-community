package com.intellij.terminal.frontend

import com.intellij.openapi.project.Project
import com.intellij.toolWindow.ToolWindowSplitContentProvider
import com.intellij.ui.content.Content
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.fus.TerminalOpeningWay
import org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo

internal class TerminalToolWindowSplitContentProvider : ToolWindowSplitContentProvider {
  override fun createContentCopy(project: Project, content: Content): Content {
    val startupFusInfo = TerminalStartupFusInfo(TerminalOpeningWay.SPLIT_TOOLWINDOW)
    val manager = TerminalToolWindowManager.getInstance(project)
    // TODO: start the new session in the same working directory as the provided content's session
    return manager.createTerminalContent(
      manager.terminalRunner,
      TerminalOptionsProvider.instance.terminalEngine,
      null,
      null,
      null,
      startupFusInfo,
      true,
      null,
    )
  }
}