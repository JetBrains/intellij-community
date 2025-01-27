package com.intellij.terminal.backend

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.block.reworked.session.TerminalSession

@Service(Service.Level.PROJECT)
internal class TerminalSessionsManager {
  suspend fun startTerminalSession(options: ShellStartupOptions): TerminalSession {
    TODO("Not yet implemented")
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): TerminalSessionsManager {
      return project.service()
    }
  }
}