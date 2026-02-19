package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.terminal.frontend.toolwindow.findTabByContent
import com.intellij.ui.content.Content
import org.jetbrains.plugins.terminal.TerminalTabCloseListener

internal class TerminalTabCloseListenerImpl private constructor(
  content: Content,
  project: Project,
  parentDisposable: Disposable,
) : TerminalTabCloseListener(content, project, parentDisposable) {
  override fun hasChildProcesses(content: Content): Boolean {
    val terminalView = TerminalToolWindowTabsManager.getInstance(myProject).findTabByContent(content)?.view
                       ?: return false
    return runWithModalProgressBlocking(myProject, "") {
      terminalView.hasChildProcesses()
    }
  }

  companion object {
    @JvmStatic
    fun install(content: Content, project: Project, parentDisposable: Disposable) {
      TerminalTabCloseListenerImpl(content, project, parentDisposable)
    }
  }
}