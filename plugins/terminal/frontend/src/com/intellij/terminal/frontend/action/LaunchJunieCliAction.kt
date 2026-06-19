// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.terminal.agent.TerminalAgent
import org.jetbrains.plugins.terminal.fus.TerminalOpeningWay
import org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo

/**
 * Launches the Junie CLI agent in a new terminal session.
 *
 * Registered under the stable ID `Terminal.AiAgents.LaunchJunieCli`. This ID and the action's
 * enabled/launch contract are a quasi-public surface: the deprecated external standalone Junie
 * plugin (compiled against earlier public APIs) looks the action up by ID, checks whether it is
 * enabled to decide between launching the CLI and falling back to the Junie web landing page, and
 * invokes it programmatically. Keep the ID and behavior stable even if the internals change.
 */
internal class LaunchJunieCliAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = project != null &&
                                         isTerminalAgentsEnabled() &&
                                         findAvailableTerminalAgentEntry(project, TerminalAgent.AgentKey(JUNIE_AGENT_KEY)) != null

    // Best-effort: warm the availability cache when it is cold, so a subsequent (re)check by the
    // caller reflects the real availability. Guarded to the empty-cache case to avoid overwriting
    // seeded state and to avoid frequent refreshes.
    if (project != null && isTerminalAgentsEnabled() &&
        TerminalAgentsAvailabilityService.getInstance(project).getAvailableAgents().isEmpty()) {
      TerminalAgentsAvailabilityService.getInstance(project).prewarm()
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val junieAgentKey = TerminalAgent.AgentKey(JUNIE_AGENT_KEY)
    // Defensive re-check to cover a possible TOCTOU between update() and actionPerformed().
    if (findAvailableTerminalAgentEntry(project, junieAgentKey) == null) return

    // Launch Junie directly via the shared helper (which also records it as the last launched agent),
    // instead of re-dispatching LaunchSelectedAgentAction.
    launchTerminalAgent(project, junieAgentKey, null, TerminalStartupFusInfo(TerminalOpeningWay.AI_AGENTS_BUTTON))
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  companion object {
    private const val JUNIE_AGENT_KEY: String = "junie"
  }
}
