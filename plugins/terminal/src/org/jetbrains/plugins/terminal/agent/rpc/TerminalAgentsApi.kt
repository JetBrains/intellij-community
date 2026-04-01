// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.agent.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.agent.TerminalAgent

@ApiStatus.Internal
@Serializable
enum class TerminalAgentMode {
  RUN,
  INSTALL_AND_RUN,
}

@ApiStatus.Internal
@Serializable
data class TerminalAvailableAgentDto(
  val agentKey: TerminalAgent.AgentKey,
  val mode: TerminalAgentMode,
)

@ApiStatus.Internal
@Serializable
data class TerminalAgentLaunchSpecDto(
  val command: List<String>,
  val mode: TerminalAgentMode,
)

@ApiStatus.Internal
@Rpc
interface TerminalAgentsApi : RemoteApi<Unit> {
  suspend fun listAvailableAgents(projectId: ProjectId): List<TerminalAvailableAgentDto>

  suspend fun resolveLaunchSpec(projectId: ProjectId, agentKey: TerminalAgent.AgentKey): TerminalAgentLaunchSpecDto?

  companion object {
    @JvmStatic
    suspend fun getInstance(): TerminalAgentsApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<TerminalAgentsApi>())
    }
  }
}
