package com.intellij.terminal.backend

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.block.reworked.session.TerminalSession

@Service(Service.Level.PROJECT)
internal class TerminalSessionsManager(private val project: Project, private val coroutineScope: CoroutineScope) {
  fun startTerminalSession(options: ShellStartupOptions): TerminalSession {
    return startTerminalSession(
      project,
      options,
      JBTerminalSystemSettingsProvider(),
      coroutineScope.childScope("TerminalSession")
    )
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): TerminalSessionsManager {
      return project.service()
    }
  }
}