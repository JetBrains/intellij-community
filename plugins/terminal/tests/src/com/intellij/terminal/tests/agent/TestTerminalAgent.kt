// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.agent

import org.jetbrains.plugins.terminal.agent.TerminalAgent
import org.jetbrains.plugins.terminal.agent.TerminalAgentProvider
import javax.swing.Icon

internal data class TestTerminalAgent(
  override val binaryName: String = "agent",
  override val agentKey: TerminalAgent.AgentKey = TerminalAgent.AgentKey(binaryName),
  override val displayName: String = binaryName,
  override val installActionText: String? = null,
  override val secondaryText: String? = null,
  override val showsNewBadge: Boolean = false,
  override val posixKnownLocationCandidates: List<String> = emptyList(),
  override val windowsKnownLocationCandidates: List<String> = emptyList(),
  override val windowsExecutableExtensions: List<String> = listOf("exe", "bat", "cmd", "ps1"),
  override val icon: Icon? = null,
) : TerminalAgent

internal class FakeTerminalAgentProvider(private vararg val agents: TerminalAgent) : TerminalAgentProvider {
  override fun getTerminalAgents(): List<TerminalAgent> = agents.toList()
}
