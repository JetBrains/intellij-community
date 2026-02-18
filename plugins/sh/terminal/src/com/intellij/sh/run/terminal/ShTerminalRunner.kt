// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.run.terminal;

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.sh.run.ShRunner
import com.intellij.terminal.JBTerminalWidget
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.TerminalUtil
import org.jetbrains.plugins.terminal.arrangement.TerminalWorkingDirectoryManager

open class ShTerminalRunner : ShRunner {
  override fun run(
    project: Project,
    command: String,
    workingDirectory: String,
    title: String,
    activateToolWindow: Boolean,
  ) {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID) ?: return
    val contentManager = toolWindow.getContentManager()
    val pair = getSuitableProcess(project, contentManager, workingDirectory)
    if (pair == null) {
      TerminalToolWindowManager.getInstance(project)
        .createShellWidget(workingDirectory, title, activateToolWindow, activateToolWindow)
        .sendCommandToExecute(command)
    }
    else {
      if (activateToolWindow) {
        toolWindow.activate(null)
      }
      pair.first.setDisplayName(title)
      contentManager.setSelectedContent(pair.first)
      pair.second.sendCommandToExecute(command)
    }
  }

  override fun isAvailable(project: Project): Boolean {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)
    return toolWindow != null && toolWindow.isAvailable()
  }

  private fun getSuitableProcess(
    project: Project,
    contentManager: ContentManager,
    workingDirectory: String,
  ): Pair<Content, TerminalWidget>? {
    val selectedContent = contentManager.getSelectedContent()
    if (selectedContent != null) {
      val pair = getSuitableProcess(project, selectedContent, workingDirectory)
      if (pair != null) return pair
    }

    return contentManager.contents.firstNotNullOfOrNull { getSuitableProcess(project, it, workingDirectory) }
  }

  protected open fun getSuitableProcess(
    project: Project,
    content: Content,
    workingDirectory: String,
  ): Pair<Content, TerminalWidget>? {
    val widget = TerminalToolWindowManager.findWidgetByContent(content)
    if (widget == null || (widget is JBTerminalWidget && widget !is ShellTerminalWidget)) {
      return null
    }

    if (widget is ShellTerminalWidget && widget.typedShellCommand.isNotEmpty()) {
      return null
    }

    val processTtyConnector = ShellTerminalWidget.getProcessTtyConnector(widget.ttyConnector)
    if (processTtyConnector == null || TerminalUtil.hasRunningCommands(processTtyConnector)) {
      return null
    }

    val currentWorkingDirectory = TerminalWorkingDirectoryManager.getWorkingDirectory(widget)
    if (!FileUtil.pathsEqual(workingDirectory, currentWorkingDirectory)) {
      return null
    }

    return Pair(content, widget)
  }
}
