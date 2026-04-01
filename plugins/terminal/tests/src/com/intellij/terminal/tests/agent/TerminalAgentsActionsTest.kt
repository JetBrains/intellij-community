// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.agent

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.terminal.frontend.action.TerminalAgentsAvailabilityService
import org.jetbrains.plugins.terminal.agent.TerminalAgentsStateService
import com.intellij.terminal.frontend.action.createTerminalAgentActions
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.agent.TERMINAL_AI_AGENTS_REGISTRY_KEY
import org.jetbrains.plugins.terminal.agent.TerminalAgent
import org.jetbrains.plugins.terminal.agent.rpc.TerminalAgentMode
import org.jetbrains.plugins.terminal.agent.rpc.TerminalAvailableAgentDto
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class TerminalAgentsActionsTest : BasePlatformTestCase() {
  override fun setUp() {
    super.setUp()
    Registry.get(TERMINAL_AI_AGENTS_REGISTRY_KEY).setValue(true, testRootDisposable)
    TerminalAgentsStateService.getInstance().isSelectorVisible = true
    TerminalAgentsStateService.getInstance().lastLaunchedAgentKey = null
  }

  @Test
  fun `creating selector actions stops showing Junie badge after counter is exhausted`() {
    setAvailableAgents(listOf(TerminalAvailableAgentDto(TerminalAgent.AgentKey("junie"), TerminalAgentMode.RUN)))
    TerminalAgentsStateService.getInstance().resetJunieNewBadgePresentations()

    repeat(TerminalAgentsStateService.INITIAL_JUNIE_NEW_BADGE_SHOW_COUNT) {
      val actions = createTerminalAgentActions(project)
      assertThat(secondaryIcon(actions.single())).isNotNull()
    }

    val exhaustedBatch = createTerminalAgentActions(project)
    assertThat(secondaryIcon(exhaustedBatch.single())).isNull()
  }

  @Test
  fun `toolbar shows AI Agents button before any agent is selected`() {
    setAvailableAgents(listOf(TerminalAvailableAgentDto(TerminalAgent.AgentKey("junie"), TerminalAgentMode.RUN)))

    assertThat(isVisible("Terminal.AiAgents.AgentSelector")).isTrue()
    assertThat(isVisible("Terminal.AiAgents.ChevronSelector")).isFalse()
    assertThat(isVisible("Terminal.AiAgents.LaunchSelectedAgent")).isFalse()
  }

  @Test
  fun `toolbar shows launch action and chevron after selecting an agent`() {
    setAvailableAgents(listOf(TerminalAvailableAgentDto(TerminalAgent.AgentKey("junie"), TerminalAgentMode.RUN)))
    TerminalAgentsStateService.getInstance().lastLaunchedAgentKey = TerminalAgent.AgentKey("junie")

    assertThat(isVisible("Terminal.AiAgents.AgentSelector")).isFalse()
    assertThat(isVisible("Terminal.AiAgents.ChevronSelector")).isTrue()
    assertThat(isVisible("Terminal.AiAgents.LaunchSelectedAgent")).isTrue()
  }

  @Test
  fun `toolbar falls back to AI Agents button when selected agent is unavailable`() {
    setAvailableAgents(listOf(TerminalAvailableAgentDto(TerminalAgent.AgentKey("codex"), TerminalAgentMode.RUN)))
    TerminalAgentsStateService.getInstance().lastLaunchedAgentKey = TerminalAgent.AgentKey("junie")

    assertThat(isVisible("Terminal.AiAgents.AgentSelector")).isTrue()
    assertThat(isVisible("Terminal.AiAgents.ChevronSelector")).isFalse()
    assertThat(isVisible("Terminal.AiAgents.LaunchSelectedAgent")).isFalse()
  }

  private fun secondaryIcon(action: AnAction): Any? {
    val event = TestActionEvent.createTestEvent(projectDataContext())
    action.update(event)
    return event.presentation.getClientProperty(ActionUtil.SECONDARY_ICON)
  }

  private fun isVisible(actionId: String): Boolean {
    val action = ActionManager.getInstance().getAction(actionId)
    val event = TestActionEvent.createTestEvent(projectDataContext())
    action.update(event)
    return event.presentation.isEnabledAndVisible
  }

  private fun projectDataContext(): DataContext = DataContext { dataId ->
    if (CommonDataKeys.PROJECT.`is`(dataId)) project else null
  }

  private fun setAvailableAgents(availableAgents: List<TerminalAvailableAgentDto>) {
    TerminalAgentsAvailabilityService.getInstance(project).setCachedAvailableAgents(availableAgents)
  }
}
