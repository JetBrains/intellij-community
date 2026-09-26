// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.action

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.ui.content.ContentManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.agent.TerminalAgent
import org.jetbrains.plugins.terminal.agent.TerminalAgentResolver
import org.jetbrains.plugins.terminal.agent.rpc.TerminalAgentMode
import org.jetbrains.plugins.terminal.startup.TerminalProcessType
import org.jetbrains.plugins.terminal.util.terminalProjectScope

internal data class TerminalAvailableAgentEntry(
  val terminalAgent: TerminalAgent,
  val mode: TerminalAgentMode,
)

internal fun getAvailableTerminalAgentEntries(project: Project): List<TerminalAvailableAgentEntry> {
  val availableByKey = TerminalAgentsAvailabilityService.getInstance(project)
    .getAvailableAgents()
    .associateBy { it.agentKey }
  return TerminalAgent.getAllTerminalAgents().mapNotNull { terminalAgent ->
    availableByKey[terminalAgent.agentKey]?.let { TerminalAvailableAgentEntry(terminalAgent, it.mode) }
  }
}

internal fun findAvailableTerminalAgentEntry(project: Project, agentKey: TerminalAgent.AgentKey?): TerminalAvailableAgentEntry? {
  return agentKey?.let { key ->
    getAvailableTerminalAgentEntries(project).firstOrNull { it.terminalAgent.agentKey == key }
  }
}

internal fun launchTerminalAgent(
  project: Project,
  agentKey: TerminalAgent.AgentKey,
  contentManager: ContentManager?,
) {
  terminalProjectScope(project).launch {
    val launchSpec = try {
      TerminalAgentResolver.resolveLaunchSpec(project, agentKey)
    }
    catch(e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      thisLogger().error(e)
      null
    }

    if (launchSpec == null) {
      TerminalAgentsAvailabilityService.getInstance(project).refreshAvailableAgents()
      return@launch
    }

    val terminalAgent = TerminalAgent.findByKey(agentKey) ?: return@launch
    withContext(Dispatchers.EDT) {
      if (project.isDisposed) return@withContext

      val tab = TerminalToolWindowTabsManager.getInstance(project).createTabBuilder()
        .shellCommand(launchSpec.command)
        .processType(TerminalProcessType.NON_SHELL)
        .tabName(terminalAgent.displayName)
        .closeOnProcessTermination(
          shouldClose = TerminalOptionsProvider.instance.closeSessionOnLogout &&
            launchSpec.mode != TerminalAgentMode.INSTALL_AND_RUN
        )
        .createTab()

      if (terminalAgent.showIconInTab && terminalAgent.icon != null) {
        tab.content.putUserData(ToolWindow.SHOW_CONTENT_ICON, true)
        tab.content.icon = terminalAgent.icon
      }
    }
  }
}