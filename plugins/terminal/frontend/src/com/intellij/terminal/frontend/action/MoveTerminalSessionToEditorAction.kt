// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal.frontend.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.terminal.JBTerminalWidget
import com.intellij.terminal.JBTerminalWidgetListener
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.content.Content
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.action.TerminalSessionContextMenuActionBase
import org.jetbrains.plugins.terminal.vfs.TerminalSessionVirtualFileImpl
import java.lang.Boolean
import kotlin.let

internal class MoveTerminalSessionToEditorAction : TerminalSessionContextMenuActionBase(), DumbAware {
  override fun updateInTerminalToolWindow(e: AnActionEvent, project: Project, content: Content, terminalWidget: TerminalWidget) {
    e.presentation.isEnabledAndVisible = !Registry.`is`("toolwindow.open.tab.in.editor")
  }

  override fun actionPerformedInTerminalToolWindow(e: AnActionEvent, project: Project, content: Content, terminalWidget: TerminalWidget) {
    val terminalToolWindowManager = TerminalToolWindowManager.getInstance(project)
    val file = TerminalSessionVirtualFileImpl(terminalWidget.terminalTitle.buildTitle(), terminalWidget, terminalToolWindowManager.terminalRunner.settingsProvider)
    file.putUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN, Boolean.TRUE)
    FileEditorManager.getInstance(project).openFile(file, true).first()
    JBTerminalWidget.asJediTermWidget(terminalWidget)?.let {
      it.listener = TerminalEditorWidgetListener(project, file)
    }

    terminalToolWindowManager.detachWidgetAndRemoveContent(content)

    file.putUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN, null)
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