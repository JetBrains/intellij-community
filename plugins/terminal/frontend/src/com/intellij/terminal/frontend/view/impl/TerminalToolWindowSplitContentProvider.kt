package com.intellij.terminal.frontend.view.impl

import com.intellij.openapi.project.Project
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.terminal.frontend.toolwindow.findTabByContent
import com.intellij.terminal.frontend.toolwindow.impl.shouldUseReworkedTerminal
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.toolWindow.ToolWindowSplitContentProvider
import com.intellij.ui.content.Content
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.TerminalTabState
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.fus.TerminalOpeningWay
import org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo
import org.jetbrains.plugins.terminal.util.getNow
import java.nio.file.Path

internal class TerminalToolWindowSplitContentProvider : ToolWindowSplitContentProvider {
  override fun createContentCopy(project: Project, content: Content): Content {
    val fusInfo = TerminalStartupFusInfo(TerminalOpeningWay.SPLIT_TOOLWINDOW)

    return if (shouldUseReworkedTerminal()) {
      createReworkedTerminalContent(project, content, fusInfo)
    }
    else {
      createClassicTerminalContent(project, content)
    }
  }

  private fun createReworkedTerminalContent(project: Project, content: Content, fusInfo: TerminalStartupFusInfo): Content {
    val manager = TerminalToolWindowTabsManager.getInstance(project)
    val originalView = manager.findTabByContent(content)?.view
    val currentDirectory = originalView?.getCurrentDirectoryPath()

    return manager.createTabBuilder()
      .workingDirectory(currentDirectory?.toString())
      .shouldAddToToolWindow(false)
      .startupFusInfo(fusInfo)
      .createTab()
      .content
  }

  private fun TerminalView.getCurrentDirectoryPath(): Path? {
    val currentDirectory = getCurrentDirectory() ?: return null
    val session = sessionDeferred.getNow() ?: return null
    return runCatching {
      EelPath.parse(currentDirectory, session.eelDescriptor).asNioPath()
    }.getOrNull()
  }

  private fun createClassicTerminalContent(
    project: Project,
    originalContent: Content,
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
      true,
      null,
    )
  }
}