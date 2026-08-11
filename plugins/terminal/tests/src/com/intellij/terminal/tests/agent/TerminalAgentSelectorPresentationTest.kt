// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.agent

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.terminal.frontend.action.TerminalAgentsSelectorPresentationUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.agent.rpc.TerminalAgentMode
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class TerminalAgentSelectorPresentationTest : BasePlatformTestCase() {
  @Test
  fun `run mode keeps secondary text visible without badge`() {
    val agent = TestTerminalAgent(displayName = "Agent", secondaryText = "by Vendor")
    val presentation = Presentation()

    TerminalAgentsSelectorPresentationUtil.applyToPresentation(presentation, agent, TerminalAgentMode.RUN, showNewBadge = false)

    assertThat(presentation.text).startsWith("<html>")
    assertThat(presentation.text).contains("Agent").contains("by Vendor")
    assertThat(presentation.getClientProperty(ActionUtil.SECONDARY_TEXT)).isNull()
    assertThat(presentation.getClientProperty(ActionUtil.SECONDARY_ICON)).isNull()
  }

  @Test
  fun `install mode shows install action text before secondary text`() {
    val agent = TestTerminalAgent(displayName = "Agent", installActionText = "Install Agent CLI", secondaryText = "by Vendor")
    val presentation = Presentation()

    TerminalAgentsSelectorPresentationUtil.applyToPresentation(presentation, agent, TerminalAgentMode.INSTALL_AND_RUN, showNewBadge = true)

    assertThat(presentation.text).startsWith("<html>")
    assertThat(presentation.text).contains("Install Agent CLI").contains("by Vendor")
    assertThat(presentation.text.indexOf("Install Agent CLI")).isLessThan(presentation.text.indexOf("by Vendor"))
    assertThat(presentation.getClientProperty(ActionUtil.SECONDARY_TEXT)).isNull()
    assertThat(presentation.getClientProperty(ActionUtil.SECONDARY_ICON)).isEqualTo(AllIcons.General.New_badge)
  }

  @Test
  fun `install mode falls back to display name when install action text is absent`() {
    val agent = TestTerminalAgent(displayName = "Agent")
    val presentation = Presentation()

    TerminalAgentsSelectorPresentationUtil.applyToPresentation(presentation, agent, TerminalAgentMode.INSTALL_AND_RUN, showNewBadge = false)

    assertThat(presentation.text).isEqualTo("Agent")
  }

  @Test
  fun `agents without secondary text keep plain selector text`() {
    val agent = TestTerminalAgent(displayName = "Agent")
    val presentation = Presentation()

    TerminalAgentsSelectorPresentationUtil.applyToPresentation(presentation, agent, TerminalAgentMode.RUN, showNewBadge = false)

    assertThat(presentation.text).isEqualTo("Agent")
    assertThat(presentation.getClientProperty(ActionUtil.SECONDARY_TEXT)).isNull()
    assertThat(presentation.getClientProperty(ActionUtil.SECONDARY_ICON)).isNull()
  }

  @Test
  fun `run mode can render new badge`() {
    val agent = TestTerminalAgent(displayName = "Agent", secondaryText = "by Vendor")
    val presentation = Presentation()

    TerminalAgentsSelectorPresentationUtil.applyToPresentation(presentation, agent, TerminalAgentMode.RUN, showNewBadge = true)

    assertThat(presentation.text).contains("Agent").contains("by Vendor")
    assertThat(presentation.getClientProperty(ActionUtil.SECONDARY_TEXT)).isNull()
    assertThat(presentation.getClientProperty(ActionUtil.SECONDARY_ICON)).isEqualTo(AllIcons.General.New_badge)
  }
}
