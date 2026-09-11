// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.sh.terminal.frontend

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.sh.ShBundle
import com.intellij.sh.run.ShSelectedTerminalRunner
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.terminal.frontend.toolwindow.getTerminalTab
import com.intellij.terminal.frontend.toolwindow.impl.shouldUseReworkedTerminal
import com.intellij.ui.content.Content
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import java.awt.Component

internal class ShFrontendTerminalRunner : ShSelectedTerminalRunner {
  override fun run(
    project: Project,
    command: String,
    @NlsContexts.TabTitle title: String,
    runInNewTerminal: Runnable,
  ): Boolean {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID) ?: return false
    run(toolWindow, command, title, runInNewTerminal)
    return true
  }

  internal fun run(
    toolWindow: ToolWindow,
    command: String,
    @NlsContexts.TabTitle title: String,
    runInNewTerminal: Runnable,
  ) {
    val selectedContent = toolWindow.contentManager.selectedContent
    if (selectedContent != null && runInTerminalContent(toolWindow, selectedContent, command, title)) {
      return
    }
    runInNewTerminal.run()
  }

  internal fun sendCommand(
    content: Content,
    command: String,
    @NlsContexts.TabTitle title: String,
  ): Boolean {
    val terminalView = content.getTerminalTab()?.view
    val terminalTitle = if (terminalView != null) {
      terminalView.createSendTextBuilder().shouldExecute().send(command)
      terminalView.title
    }
    else {
      val terminalWidget = TerminalToolWindowManager.findWidgetByContent(content) ?: return false
      terminalWidget.sendCommandToExecute(command)
      terminalWidget.terminalTitle
    }
    terminalTitle.change {
      if (userDefinedTitle == null || userDefinedTitle == content.getUserData(LAST_AUTOMATIC_TITLE)) {
        userDefinedTitle = title
        markAutomaticTitle(content, title)
      }
    }
    return true
  }

  override fun hasTerminalStartedFrom(project: Project, sourceFileUrl: String): Boolean {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID) ?: return false
    return hasTerminalStartedFrom(toolWindow.contentManager.contentsRecursively, sourceFileUrl)
  }

  internal fun associateTerminal(content: Content, sourceFileUrl: String) {
    content.putUserData(MARKDOWN_SOURCE_FILE_URL, sourceFileUrl)
  }

  internal fun markAutomaticTitle(content: Content, @NlsContexts.TabTitle title: String) {
    content.putUserData(LAST_AUTOMATIC_TITLE, title)
  }

  internal fun hasTerminalStartedFrom(contents: List<Content>, sourceFileUrl: String): Boolean {
    return contents.any { it.getUserData(MARKDOWN_SOURCE_FILE_URL) == sourceFileUrl }
  }

  override fun showChooser(
    project: Project,
    command: String,
    @NlsContexts.TabTitle title: String,
    component: Component,
    x: Int,
    y: Int,
    runInNewTerminal: Runnable,
  ): Boolean {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID) ?: return false
    val terminalContents = toolWindow.contentManager.contentsRecursively.filter(::isTerminal)
    val actions = DefaultActionGroup()
    actions.add(DumbAwareAction.create(ShBundle.message("sh.markdown.runner.terminal.new")) {
      runInNewTerminal.run()
    })
    if (terminalContents.isNotEmpty()) {
      actions.addSeparator()
    }
    terminalContents.forEach { content ->
      actions.add(DumbAwareAction.create(content.displayName) {
        runInTerminalContent(toolWindow, content, command, title)
      })
    }
    ActionManager.getInstance()
      .createActionPopupMenu(ActionPlaces.EDITOR_GUTTER_POPUP, actions)
      .component
      .show(component, x, y)
    return true
  }

  @Suppress("DEPRECATION")
  override fun createNewTerminal(
    project: Project,
    command: String,
    workingDirectory: String,
    @NlsContexts.TabTitle title: String,
    sourceFileUrl: String?,
  ) {
    if (shouldUseReworkedTerminal()) {
      val tab = TerminalToolWindowTabsManager.getInstance(project)
        .createTabBuilder()
        .workingDirectory(workingDirectory)
        .tabName(title)
        .requestFocus(true)
        .createTab()
      if (sourceFileUrl != null) associateTerminal(tab.content, sourceFileUrl)
      markAutomaticTitle(tab.content, title)
      tab.view.createSendTextBuilder().shouldExecute().send(command)
      return
    }

    val toolWindowManager = TerminalToolWindowManager.getInstance(project)
    val widget = toolWindowManager.createShellWidget(workingDirectory, title, true, true)
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)
    toolWindow?.contentManager?.contentsRecursively
      ?.firstOrNull { TerminalToolWindowManager.findWidgetByContent(it) === widget }
      ?.let { content ->
        if (sourceFileUrl != null) associateTerminal(content, sourceFileUrl)
        markAutomaticTitle(content, title)
      }
    widget.sendCommandToExecute(command)
  }

  private fun runInTerminalContent(
    toolWindow: ToolWindow,
    content: Content,
    command: String,
    @NlsContexts.TabTitle title: String,
  ): Boolean {
    if (!sendCommand(content, command, title)) return false

    toolWindow.activate(null)
    content.manager?.setSelectedContent(content)
    return true
  }

  private fun isTerminal(content: Content): Boolean {
    return content.getTerminalTab()?.view != null || TerminalToolWindowManager.findWidgetByContent(content) != null
  }

  companion object {
    internal val MARKDOWN_SOURCE_FILE_URL: Key<String> = Key.create("Shell.Markdown.Source.File.Url")
    internal val LAST_AUTOMATIC_TITLE: Key<@NlsContexts.TabTitle String> = Key.create("Shell.Markdown.Last.Automatic.Title")
  }
}
