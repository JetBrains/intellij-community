// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.terminal.JBTerminalWidget
import com.intellij.ui.content.Content
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@Deprecated("Use TerminalToolWindowManager instead", ReplaceWith("TerminalToolWindowManager"))
@ApiStatus.ScheduledForRemoval
@Suppress("unused", "DEPRECATION")
@Service(Service.Level.PROJECT)
class TerminalView(private val project: Project) {

  private val toolWindowManager: TerminalToolWindowManager
    get() = TerminalToolWindowManager.getInstance(project)

  fun createNewSession(terminalRunner: AbstractTerminalRunner<*>) {
    toolWindowManager.createNewSession(terminalRunner)
  }

  fun createNewSession(terminalRunner: AbstractTerminalRunner<*>, tabState: TerminalTabState?) {
    toolWindowManager.createNewSession(terminalRunner, tabState)
  }

  fun getWidgets(): Set<JBTerminalWidget> {
    return toolWindowManager.widgets
  }

  fun getTerminalRunner(): AbstractTerminalRunner<*> = toolWindowManager.terminalRunner

  fun createLocalShellWidget(workingDirectory: String?,
                             tabName: @Nls String?,
                             requestFocus: Boolean,
                             deferSessionStartUntilUiShown: Boolean): ShellTerminalWidget {
    return ShellTerminalWidget.toShellJediTermWidgetOrThrow(
      toolWindowManager.createShellWidget(workingDirectory, tabName, requestFocus, deferSessionStartUntilUiShown))
  }

  fun createLocalShellWidget(workingDirectory: String?, tabName: @Nls String?): ShellTerminalWidget {
    return ShellTerminalWidget.toShellJediTermWidgetOrThrow(
      toolWindowManager.createShellWidget(workingDirectory, tabName, true, true))
  }

  fun createLocalShellWidget(workingDirectory: String?,
                             tabName: @Nls String?,
                             requestFocus: Boolean): ShellTerminalWidget {
    return ShellTerminalWidget.toShellJediTermWidgetOrThrow(
      toolWindowManager.createShellWidget(workingDirectory, tabName, requestFocus, true))
  }

  fun closeTab(content: Content) {
    toolWindowManager.closeTab(content)
  }

  fun openTerminalIn(fileToOpen: VirtualFile?) {
    toolWindowManager.openTerminalIn(fileToOpen)
  }

  companion object {
    @Deprecated("Use TerminalToolWindowManager.getInstance() instead", ReplaceWith("TerminalToolWindowManager.getInstance(project)"))
    @JvmStatic
    fun getInstance(project: Project): TerminalView {
      return project.service<TerminalView>()
    }

    @JvmStatic
    fun getWidgetByContent(content: Content): JBTerminalWidget? {
      return TerminalToolWindowManager.getWidgetByContent(content)
    }
  }
}