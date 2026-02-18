// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.run.terminal;

import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.sh.run.ShRunner
import com.intellij.terminal.JBTerminalWidget
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.content.Content
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.TerminalUtil
import org.jetbrains.plugins.terminal.arrangement.TerminalWorkingDirectoryManager

@ApiStatus.Internal
open class ShTerminalRunner : ShRunner {
  override fun run(
    project: Project,
    command: String,
    workingDirectory: String,
    title: String,
    activateToolWindow: Boolean,
  ) {
    ShTerminalScopeProvider.getInstance(project).coroutineScope.launch(Dispatchers.UiWithModelAccess) {
      doRun(project, command, workingDirectory, title, activateToolWindow)
    }
  }

  private suspend fun doRun(
    project: Project,
    command: String,
    workingDirectory: String,
    @NlsContexts.TabTitle title: String,
    activateToolWindow: Boolean,
  ) {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID) ?: return
    val contentManager = toolWindow.getContentManager()
    val contents = contentManager.contents
    val selectedContent = contentManager.selectedContent

    val pair = withContext(Dispatchers.IO) {
      getSuitableProcess(project, contents, selectedContent, workingDirectory)
    }

    if (pair == null) {
      TerminalToolWindowManager.getInstance(project)
        .createShellWidget(workingDirectory, title, activateToolWindow, activateToolWindow)
        .sendCommandToExecute(command)
    }
    else if (contentManager.contents.contains(pair.first) && pair.first.isValid) {  // Check that chosen content is still valid
      if (activateToolWindow) {
        toolWindow.activate(null)
      }
      pair.first.setDisplayName(title)
      contentManager.setSelectedContent(pair.first)
      pair.second.sendCommandToExecute(command)
    }
    else {
      // The chosen content became invalid during background checks, let's retry.
      doRun(project, command, workingDirectory, title, false)
    }
  }

  override fun isAvailable(project: Project): Boolean {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)
    return toolWindow != null && toolWindow.isAvailable()
  }

  private fun getSuitableProcess(
    project: Project,
    contents: Array<Content>,
    selectedContent: Content?,
    workingDirectory: String,
  ): Pair<Content, TerminalWidget>? {
    if (selectedContent != null) {
      val pair = getSuitableProcess(project, selectedContent, workingDirectory)
      if (pair != null) return pair
    }

    val otherContents = contents.filter { it != selectedContent }
    return otherContents.firstNotNullOfOrNull { getSuitableProcess(project, it, workingDirectory) }
  }

  @RequiresReadLockAbsence
  @RequiresBackgroundThread
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

@Service(Service.Level.PROJECT)
private class ShTerminalScopeProvider(val coroutineScope: CoroutineScope) {
  companion object {
    fun getInstance(project: Project): ShTerminalScopeProvider = project.service()
  }
}
