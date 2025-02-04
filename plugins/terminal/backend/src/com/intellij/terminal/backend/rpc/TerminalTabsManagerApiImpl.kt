package com.intellij.terminal.backend.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.terminal.backend.TerminalTabsManager
import org.jetbrains.plugins.terminal.block.reworked.session.ShellStartupOptionsDto
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionId
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalTabsManagerApi
import org.jetbrains.plugins.terminal.block.reworked.session.toShellStartupOptions

internal class TerminalTabsManagerApiImpl : TerminalTabsManagerApi {
  override suspend fun startTerminalSession(projectId: ProjectId, options: ShellStartupOptionsDto): TerminalSessionId {
    val project = projectId.findProject()
    val sessionsManager = TerminalTabsManager.getInstance(project)
    return sessionsManager.startTerminalSession(options.toShellStartupOptions())
  }
}