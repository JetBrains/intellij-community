package com.intellij.terminal.frontend

import com.intellij.openapi.project.Project
import com.intellij.toolWindow.ToolWindowSplitContentProvider
import com.intellij.ui.content.Content
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.TerminalTabState
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.fus.TerminalOpeningWay
import org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo

internal class TerminalToolWindowSplitContentProvider : ToolWindowSplitContentProvider {
  override fun createContentCopy(project: Project, content: Content): Content {
    val startupFusInfo = TerminalStartupFusInfo(TerminalOpeningWay.SPLIT_TOOLWINDOW)
    val manager = TerminalToolWindowManager.getInstance(project)

    val widget = TerminalToolWindowManager.findWidgetByContent(content)
    val currentDirectory = widget?.getCurrentDirectory()
    val tabState = TerminalTabState().also {
      it.myWorkingDirectory = currentDirectory
    }

    return manager.createTerminalContent(
      manager.terminalRunner,
      TerminalOptionsProvider.instance.terminalEngine,
      null,
      tabState,
      null,
      startupFusInfo,
      true,
      null,
    )
  }
}