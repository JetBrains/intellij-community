package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.terminal.frontend.toolwindow.findTabByContent
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import com.intellij.ui.content.Content
import org.jetbrains.plugins.terminal.TerminalTabCloseListener
import org.jetbrains.plugins.terminal.startup.TerminalProcessType
import org.jetbrains.plugins.terminal.util.getNow

internal class TerminalTabCloseListenerImpl private constructor(
  content: Content,
  project: Project,
  parentDisposable: Disposable,
) : TerminalTabCloseListener(content, project, parentDisposable) {
  override fun shouldConfirmClosing(content: Content): Boolean {
    val terminalView = TerminalToolWindowTabsManager.getInstance(myProject).findTabByContent(content)?.view
                       ?: return false

    if (terminalView.sessionState.value != TerminalViewSessionState.Running) {
      return false
    }

    val startupOptions = terminalView.startupOptionsDeferred.getNow()
    if (startupOptions?.processType == TerminalProcessType.NON_SHELL) {
      // If some non-shell process is running, consider that confirmation for closing is required.
      return true
    }

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