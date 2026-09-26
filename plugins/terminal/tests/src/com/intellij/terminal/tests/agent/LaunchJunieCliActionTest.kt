// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.agent

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.frontend.action.TerminalAgentsAvailabilityService
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.agent.TerminalAgent
import org.jetbrains.plugins.terminal.agent.TerminalAgentProvider
import org.jetbrains.plugins.terminal.agent.rpc.TerminalAgentMode
import org.jetbrains.plugins.terminal.agent.rpc.TerminalAvailableAgentDto
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private const val ACTION_ID = "Terminal.AiAgents.LaunchJunieCli"

@RunWith(JUnit4::class)
internal class LaunchJunieCliActionTest : BasePlatformTestCase() {
  override fun setUp() {
    super.setUp()
    // The Junie/Codex agent keys used below are stable IDs referenced by the hardcoded LaunchJunieCliAction;
    // the fake provider only needs to make those keys resolvable.
    ExtensionTestUtil.maskExtensions(
      TerminalAgentProvider.EP_NAME,
      listOf(
        FakeTerminalAgentProvider(
          TestTerminalAgent(agentKey = TerminalAgent.AgentKey("junie")),
          TestTerminalAgent(agentKey = TerminalAgent.AgentKey("codex")),
        ),
      ),
      testRootDisposable,
    )
    ToolWindowManager.getInstance(project).registerToolWindow(RegisterToolWindowTask(id = TerminalToolWindowFactory.TOOL_WINDOW_ID))
  }

  @Test
  fun `launch Junie CLI action is resolvable by id`() {
    assertThat(ActionManager.getInstance().getAction(ACTION_ID)).isNotNull()
  }

  @Test
  fun `launch Junie CLI action is enabled when Junie is available`() {
    setAvailableAgents(listOf(TerminalAvailableAgentDto(TerminalAgent.AgentKey("junie"), TerminalAgentMode.RUN)))

    assertThat(isVisible()).isTrue()
  }

  @Test
  fun `launch Junie CLI action is enabled when Junie is available in install-and-run mode`() {
    setAvailableAgents(listOf(TerminalAvailableAgentDto(TerminalAgent.AgentKey("junie"), TerminalAgentMode.INSTALL_AND_RUN)))

    assertThat(isVisible()).isTrue()
  }

  @Test
  fun `launch Junie CLI action is disabled when only another agent is available`() {
    setAvailableAgents(listOf(TerminalAvailableAgentDto(TerminalAgent.AgentKey("codex"), TerminalAgentMode.RUN)))

    assertThat(isVisible()).isFalse()
  }

  @Test
  fun `launch Junie CLI action is disabled when no agents are available`() {
    setAvailableAgents(emptyList())

    assertThat(isVisible()).isFalse()
  }

  @Test
  fun `launch Junie CLI action does not launch when Junie is unavailable despite an enabled presentation`() {
    // Simulate a TOCTOU between update() and actionPerformed(): the presentation is stale-enabled, but Junie
    // is no longer among the available agents. The action's defensive re-check must then skip the launch.
    setAvailableAgents(listOf(TerminalAvailableAgentDto(TerminalAgent.AgentKey("codex"), TerminalAgentMode.RUN)))
    val action = ActionManager.getInstance().getAction(ACTION_ID)
    val event = TestActionEvent.createTestEvent(projectDataContext())
    event.presentation.isEnabledAndVisible = true

    ActionUtil.performAction(action, event)

    assertThat(TerminalToolWindowTabsManager.getInstance(project).tabs).isEmpty()
  }

  private fun isVisible(): Boolean {
    val action = ActionManager.getInstance().getAction(ACTION_ID)
    val event = TestActionEvent.createTestEvent(projectDataContext())
    ActionUtil.updateAction(action, event)
    return event.presentation.isEnabledAndVisible
  }

  private fun projectDataContext(): DataContext = DataContext { dataId ->
    if (CommonDataKeys.PROJECT.`is`(dataId)) project else null
  }

  private fun setAvailableAgents(availableAgents: List<TerminalAvailableAgentDto>) {
    TerminalAgentsAvailabilityService.getInstance(project).setCachedAvailableAgents(availableAgents)
  }
}
