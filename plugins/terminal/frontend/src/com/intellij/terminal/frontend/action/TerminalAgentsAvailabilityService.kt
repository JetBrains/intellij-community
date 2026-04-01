// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.action

import com.intellij.ide.ActivityTracker
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.terminal.agent.rpc.TerminalAgentsApi
import org.jetbrains.plugins.terminal.agent.rpc.TerminalAvailableAgentDto

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class TerminalAgentsAvailabilityService(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) {
  @Volatile
  private var cachedAvailableAgents: List<TerminalAvailableAgentDto> = emptyList()

  fun getAvailableAgents(): List<TerminalAvailableAgentDto> {
    return if (isTerminalAgentsEnabled()) cachedAvailableAgents else emptyList()
  }

  fun prewarm() {
    coroutineScope.launch {
      refreshAvailableAgents()
    }
  }

  suspend fun refreshAvailableAgents(): List<TerminalAvailableAgentDto> {
    val availableAgents = when {
      project.isDisposed -> emptyList()
      !isTerminalAgentsEnabled() -> emptyList()
      else -> {
        try {
          val allAgents = TerminalAgentsApi.getInstance().listAvailableAgents(project.projectId())
          thisLogger().debug {
            "Terminal AI agents availability: ${allAgents.joinToString { "${it.agentKey}=${it.mode}" }}"
          }
          allAgents
        }
        catch (t: Throwable) {
          thisLogger().warn("Failed to fetch terminal AI agent availability", t)
          emptyList()
        }
      }
    }

    if (cachedAvailableAgents != availableAgents) {
      cachedAvailableAgents = availableAgents
      ActivityTracker.getInstance().inc()
    }
    return availableAgents
  }

  @TestOnly
  fun setCachedAvailableAgents(available: List<TerminalAvailableAgentDto>) {
    cachedAvailableAgents = available
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): TerminalAgentsAvailabilityService = project.service()
  }
}
