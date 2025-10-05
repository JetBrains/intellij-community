// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal.frontend.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowContextMenuActionBase
import com.intellij.terminal.JBTerminalWidget
import com.intellij.terminal.JBTerminalWidgetListener
import com.intellij.terminal.frontend.editor.TerminalViewVirtualFile
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.terminal.frontend.toolwindow.findTabByContent
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.content.Content
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.ui.TerminalContainer
import org.jetbrains.plugins.terminal.vfs.TerminalSessionVirtualFileImpl

internal class MoveTerminalSessionToEditorAction : ToolWindowContextMenuActionBase(), DumbAware {
  override fun actionPerformed(e: AnActionEvent, toolWindow: ToolWindow, content: Content?) {
    val project = e.project
    if (project == null || content == null) {
      return
    }

    val reworkedTerminalTab = findReworkedTerminalTab(project, content)
    val classicTerminal = findClassicTerminal(e, content)
    if (reworkedTerminalTab != null) {
      performForReworkedTerminalTab(project, reworkedTerminalTab)
    }
    else if (classicTerminal != null) {
      performForClassicTerminal(project, classicTerminal, content)
    }
  }

  private fun performForReworkedTerminalTab(project: Project, terminalTab: TerminalToolWindowTab) {
    val manager = TerminalToolWindowTabsManager.getInstance(project)
    manager.detachTab(terminalTab)

    val file = TerminalViewVirtualFile(terminalTab.view)
    file.putUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN, true)
    try {
      FileEditorManager.getInstance(project).openFile(file, true)
    }
    finally {
      file.putUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN, null)
    }
  }

  private fun performForClassicTerminal(project: Project, widget: TerminalWidget, content: Content) {
    val manager = TerminalToolWindowManager.getInstance(project)
    val file = TerminalSessionVirtualFileImpl(
      widget.terminalTitle.buildTitle(),
      widget,
      manager.terminalRunner.settingsProvider
    )

    file.putUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN, true)
    try {
      FileEditorManager.getInstance(project).openFile(file, true)
      JBTerminalWidget.asJediTermWidget(widget)?.let {
        it.listener = TerminalEditorWidgetListener(project, file)
      }
      manager.detachWidgetAndRemoveContent(content)
    }
    finally {
      file.putUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN, null)
    }
  }

  override fun update(e: AnActionEvent, toolWindow: ToolWindow, content: Content?) {
    val project = e.project
    if (project == null
        || !TerminalToolWindowManager.isTerminalToolWindow(toolWindow)
        || content == null
        || Registry.`is`("toolwindow.open.tab.in.editor")) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val reworkedTerminalTab = findReworkedTerminalTab(project, content)
    val classicTerminal = findClassicTerminal(e, content)
    e.presentation.isEnabledAndVisible = reworkedTerminalTab != null || classicTerminal != null
  }

  private fun findReworkedTerminalTab(project: Project, content: Content): TerminalToolWindowTab? {
    return TerminalToolWindowTabsManager.getInstance(project).findTabByContent(content)
  }

  private fun findClassicTerminal(e: AnActionEvent, content: Content): TerminalWidget? {
    val newWidget = e.dataContext.getData(TerminalContainer.TERMINAL_WIDGET_DATA_KEY)
    if (newWidget != null) return newWidget
    val terminalWidget = e.dataContext.getData(JBTerminalWidget.TERMINAL_DATA_KEY)
    return if (terminalWidget != null && UIUtil.isAncestor(content.component, terminalWidget)) {
      terminalWidget.asNewWidget()
    }
    else {
      TerminalToolWindowManager.findWidgetByContent(content)
    }
  }
}

private class TerminalEditorWidgetListener(val project: Project, val file: TerminalSessionVirtualFileImpl) : JBTerminalWidgetListener {
  override fun onNewSession() {
  }

  override fun onTerminalStarted() {
  }

  override fun onPreviousTabSelected() {
  }

  override fun onNextTabSelected() {
  }

  override fun onSessionClosed() {
    FileEditorManager.getInstance(project).closeFile(file)
  }

  override fun showTabs() {
  }
}