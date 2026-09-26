// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.agent

import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.EmptyIcon
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.agent.TerminalAgent
import org.jetbrains.plugins.terminal.agent.TerminalAgentProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import javax.swing.Icon

@RunWith(JUnit4::class)
internal class TerminalAgentTest : BasePlatformTestCase() {
  @Test
  fun `flatten providers preserves provider order and first duplicate wins`() {
    val alpha = MyTerminalAgent("alpha")
    val duplicateFromFirstProvider = MyTerminalAgent("duplicate")
    val beta = MyTerminalAgent("beta")
    val duplicateFromSecondProvider = MyTerminalAgent("duplicate")
    val gamma = MyTerminalAgent("gamma")

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
    val duplicateFromFirstProvider = MyTerminalAgent("duplicate")
    val alpha = MyTerminalAgent("alpha")
    val duplicateFromSecondProvider = MyTerminalAgent("duplicate")
    val beta = MyTerminalAgent("beta")
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

private class MyTerminalAgent(
  key: String,
) : TerminalAgent {
  override val agentKey = TerminalAgent.AgentKey(key)
  override val displayName: String = agentKey.key
  override val binaryName: String = agentKey.key
  override val icon: Icon = EmptyIcon.ICON_16
}
