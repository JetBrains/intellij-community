// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.Content
import com.intellij.ui.tabs.TabInfo
import org.jetbrains.plugins.terminal.TerminalView
import org.jetbrains.plugins.terminal.vfs.TerminalEditorWidgetListener
import org.jetbrains.plugins.terminal.vfs.TerminalSessionVirtualFileImpl

class MoveTerminalSessionToEditorAction : TerminalSessionContextMenuActionBase(), DumbAware {
  override fun actionPerformed(e: AnActionEvent, activeToolWindow: ToolWindow, selectedContent: Content?) {
    val tabInfo = TabInfo(selectedContent!!.component)
      .setText(selectedContent.displayName)
    val project = e.project!!
    val terminalView = TerminalView.getInstance(project)
    val terminalWidget = TerminalView.getWidgetByContent(selectedContent)!!
    val file = TerminalSessionVirtualFileImpl(tabInfo, terminalWidget, terminalView.terminalRunner.settingsProvider)
    tabInfo.setObject(file)
    file.putUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN, java.lang.Boolean.TRUE)
    val fileEditor = FileEditorManager.getInstance(project).openFile(file, true).first()
    terminalWidget.listener = TerminalEditorWidgetListener(project, file)

    terminalWidget.moveDisposable(fileEditor)
    terminalView.detachWidgetAndRemoveContent(selectedContent)

    file.putUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN, null)
  }
}