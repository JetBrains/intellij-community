package com.intellij.terminal.backend.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.terminal.backend.TerminalTabsManager
import org.jetbrains.plugins.terminal.block.reworked.session.ShellStartupOptionsDto
import org.jetbrains.plugins.terminal.block.reworked.session.TerminalSessionTab
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionId
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalTabsManagerApi
import org.jetbrains.plugins.terminal.block.reworked.session.toShellStartupOptions

internal class TerminalTabsManagerApiImpl : TerminalTabsManagerApi {
  override suspend fun getTerminalTabs(projectId: ProjectId): List<TerminalSessionTab> {
    val manager = getTerminalTabsManager(projectId)
    return manager.getTerminalTabs()
  }

  override suspend fun createNewTerminalTab(projectId: ProjectId, options: ShellStartupOptionsDto): TerminalSessionTab {
    val manager = getTerminalTabsManager(projectId)
    return manager.createNewTerminalTab(options.toShellStartupOptions())
  }

  override suspend fun startTerminalSessionForTab(projectId: ProjectId, tabId: Int, options: ShellStartupOptionsDto): TerminalSessionTab {
    val manager = getTerminalTabsManager(projectId)
    return manager.startTerminalSessionForTab(tabId, options.toShellStartupOptions())
  }

  override suspend fun closeTerminalTab(projectId: ProjectId, tabId: Int) {
    val manager = getTerminalTabsManager(projectId)
    manager.closeTerminalTab(tabId)
  }

  private fun getTerminalTabsManager(projectId: ProjectId): TerminalTabsManager {
    val project = projectId.findProject()
    return TerminalTabsManager.getInstance(project)
  }
}