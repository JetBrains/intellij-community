// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.agent.rpc

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
