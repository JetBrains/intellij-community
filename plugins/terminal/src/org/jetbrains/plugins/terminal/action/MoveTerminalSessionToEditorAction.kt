// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.terminal.JBTerminalWidget
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.content.Content
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.vfs.TerminalEditorWidgetListener
import org.jetbrains.plugins.terminal.vfs.TerminalSessionVirtualFileImpl

private class MoveTerminalSessionToEditorAction : TerminalSessionContextMenuActionBase(), DumbAware {
  override fun updateInTerminalToolWindow(e: AnActionEvent, project: Project, content: Content, terminalWidget: TerminalWidget) {
    e.presentation.isEnabledAndVisible = !TerminalToolWindowManager.getInstance(project).isSplitTerminal(terminalWidget)
  }

  override fun actionPerformedInTerminalToolWindow(e: AnActionEvent, project: Project, content: Content, terminalWidget: TerminalWidget) {
    val terminalToolWindowManager = TerminalToolWindowManager.getInstance(project)
    val file = TerminalSessionVirtualFileImpl(terminalWidget.terminalTitle.buildTitle(), terminalWidget, terminalToolWindowManager.terminalRunner.settingsProvider)
    file.putUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN, java.lang.Boolean.TRUE)
    FileEditorManager.getInstance(project).openFile(file, true).first()
    JBTerminalWidget.asJediTermWidget(terminalWidget)?.let {
      it.listener = TerminalEditorWidgetListener(project, file)
    }

    terminalToolWindowManager.detachWidgetAndRemoveContent(content)

    file.putUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN, null)
  }
}