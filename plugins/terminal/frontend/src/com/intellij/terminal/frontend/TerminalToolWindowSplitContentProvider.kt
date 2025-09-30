package com.intellij.terminal.frontend

import com.intellij.openapi.project.Project
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.terminal.frontend.toolwindow.impl.shouldUseReworkedTerminal
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
    return if (shouldUseReworkedTerminal()) {
      createReworkedTerminalContent(project)
    }
    else {
      createClassicTerminalContent(project, content, startupFusInfo)
    }
  }

  private fun createReworkedTerminalContent(project: Project): Content {
    // todo: determine the current directory of the existing terminal tab
    // todo: pass startupFusInfo there as well
    return TerminalToolWindowTabsManager.getInstance(project)
      .createTabBuilder()
      .shouldAddToToolWindow(false)
      .createTab()
      .content
  }

  private fun createClassicTerminalContent(
    project: Project,
    originalContent: Content,
    startupFusInfo: TerminalStartupFusInfo,
  ): Content {
    val widget = TerminalToolWindowManager.findWidgetByContent(originalContent)
    val currentDirectory = widget?.getCurrentDirectory()
    val tabState = TerminalTabState().also {
      it.myWorkingDirectory = currentDirectory
    }

    val manager = TerminalToolWindowManager.getInstance(project)
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