// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.agent

import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.EmptyIcon
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.agent.TerminalAgent
import org.jetbrains.plugins.terminal.agent.TerminalAgentProvider
import org.jetbrains.plugins.terminal.agent.DefaultTerminalAgentProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import javax.swing.Icon

@RunWith(JUnit4::class)
internal class TerminalAgentTest : BasePlatformTestCase() {
  @Test
  fun `default provider returns built-in agent terminals in expected order`() {
    val terminalAgents = DefaultTerminalAgentProvider().getTerminalAgents()

    assertThat(terminalAgents.map { it.agentKey.key }).containsExactly("junie", "claude_code", "codex")
  }

  @Test
  fun `default provider exposes bundled selector fields`() {
    val agentsByKey = DefaultTerminalAgentProvider().getTerminalAgents().associateBy { it.agentKey.key }
    val junie = agentsByKey.getValue("junie")
    val codex = agentsByKey.getValue("codex")
    val claude = agentsByKey.getValue("claude_code")

    assertThat(junie.installActionText).isEqualTo("Install Junie CLI")
    assertThat(junie.secondaryText).isEqualTo("by JetBrains")
    assertThat(junie.showsNewBadge).isTrue()

    assertThat(codex.installActionText).isNull()
    assertThat(codex.secondaryText).isNull()
    assertThat(codex.showsNewBadge).isFalse()
    assertThat(codex.unixHomeBinaryPath).isNull()
    assertThat(codex.windowsHomeBinaryPath).isNull()

    assertThat(claude.installActionText).isNull()
    assertThat(claude.secondaryText).isNull()
    assertThat(claude.showsNewBadge).isFalse()
    assertThat(claude.unixHomeBinaryPath).isNull()
    assertThat(claude.windowsHomeBinaryPath).isNull()

    assertThat(junie.unixHomeBinaryPath).isEqualTo(".local/bin/junie")
    assertThat(junie.windowsHomeBinaryPath).isEqualTo(".local\\bin\\junie.bat")
  }

  @Test
  fun `flatten providers preserves provider order and first duplicate wins`() {
    val alpha = TestTerminalAgent("alpha")
    val duplicateFromFirstProvider = TestTerminalAgent("duplicate")
    val beta = TestTerminalAgent("beta")
    val duplicateFromSecondProvider = TestTerminalAgent("duplicate")
    val gamma = TestTerminalAgent("gamma")

    val flattened = TerminalAgent.flattenProviders(
      listOf(
        TestTerminalAgentProvider(alpha, duplicateFromFirstProvider),
        TestTerminalAgentProvider(beta, duplicateFromSecondProvider, gamma),
      )
    )

    assertThat(flattened).containsExactly(alpha, duplicateFromFirstProvider, beta, gamma)
  }

  @Test
  fun `find by key returns first matching terminal from extension point`() {
    val duplicateFromFirstProvider = TestTerminalAgent("duplicate")
    val alpha = TestTerminalAgent("alpha")
    val duplicateFromSecondProvider = TestTerminalAgent("duplicate")
    val beta = TestTerminalAgent("beta")
    val firstProvider = TestTerminalAgentProvider(duplicateFromFirstProvider, alpha)
    val secondProvider = TestTerminalAgentProvider(duplicateFromSecondProvider, beta)

    ExtensionTestUtil.maskExtensions(TerminalAgentProvider.EP_NAME, listOf(firstProvider, secondProvider), testRootDisposable)

    assertThat(TerminalAgent.getAllProviders()).containsExactly(firstProvider, secondProvider)
    assertThat(TerminalAgent.getAllTerminalAgents()).containsExactly(duplicateFromFirstProvider, alpha, beta)
    assertThat(TerminalAgent.findByKey(TerminalAgent.AgentKey("duplicate"))).isSameAs(duplicateFromFirstProvider)
  }
}

private class TestTerminalAgentProvider(
  private vararg val agents: TerminalAgent,
) : TerminalAgentProvider {
  override fun getTerminalAgents(): List<TerminalAgent> = agents.toList()
}

private class TestTerminalAgent(
  key: String
) : TerminalAgent {
  override val agentKey = TerminalAgent.AgentKey(key)
  override val displayName: String = agentKey.key
  override val binaryName: String = agentKey.key
  override val unixHomeBinaryPath: String? = null
  override val windowsHomeBinaryPath: String? = null
  override val icon: Icon = EmptyIcon.ICON_16
}
