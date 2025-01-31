package com.intellij.terminal.backend.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.terminal.backend.TerminalSessionsManager
import org.jetbrains.plugins.terminal.block.reworked.session.ShellStartupOptionsDto
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionId
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionsManagerApi
import org.jetbrains.plugins.terminal.block.reworked.session.toShellStartupOptions

internal class TerminalSessionsManagerApiImpl : TerminalSessionsManagerApi {
  override suspend fun startTerminalSession(projectId: ProjectId, options: ShellStartupOptionsDto): TerminalSessionId {
    val project = projectId.findProject()
    val sessionsManager = TerminalSessionsManager.getInstance(project)
    return sessionsManager.startTerminalSession(options.toShellStartupOptions())
  }
}