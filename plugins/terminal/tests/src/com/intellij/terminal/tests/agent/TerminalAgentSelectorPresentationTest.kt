// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.agent

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.terminal.frontend.action.TerminalAgentsSelectorPresentationUtil
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.agent.DefaultTerminalAgentProvider
import org.jetbrains.plugins.terminal.agent.rpc.TerminalAgentMode
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class TerminalAgentSelectorPresentationTest : BasePlatformTestCase() {
  @Test
  fun `run mode keeps Junie secondary text visible without badge`() {
    val junie = bundledAgentByKey("junie")
    val presentation = Presentation()

    TerminalAgentsSelectorPresentationUtil.applyToPresentation(presentation, junie, TerminalAgentMode.RUN, showNewBadge = false)

    assertThat(presentation.text).startsWith("<html>")
    assertThat(presentation.text).contains("Junie").contains("by JetBrains")
    assertThat(presentation.getClientProperty(ActionUtil.SECONDARY_TEXT)).isNull()
    assertThat(presentation.getClientProperty(ActionUtil.SECONDARY_ICON)).isNull()
  }

  @Test
  fun `install mode keeps Junie secondary text before new badge`() {
    val junie = bundledAgentByKey("junie")
    val presentation = Presentation()

    TerminalAgentsSelectorPresentationUtil.applyToPresentation(presentation, junie, TerminalAgentMode.INSTALL_AND_RUN, showNewBadge = true)

    assertThat(presentation.text).startsWith("<html>")
    assertThat(presentation.text).contains("Install Junie CLI").contains("by JetBrains")
    assertThat(presentation.text.indexOf("Install Junie CLI")).isLessThan(presentation.text.indexOf("by JetBrains"))
    assertThat(presentation.getClientProperty(ActionUtil.SECONDARY_TEXT)).isNull()
    assertThat(presentation.getClientProperty(ActionUtil.SECONDARY_ICON)).isEqualTo(AllIcons.General.New_badge)
  }

  @Test
  fun `plain agents keep plain selector text`() {
    val codex = bundledAgentByKey("codex")
    val presentation = Presentation()

    TerminalAgentsSelectorPresentationUtil.applyToPresentation(presentation, codex, TerminalAgentMode.RUN, showNewBadge = false)

    assertThat(presentation.text).isEqualTo("Codex")
    assertThat(presentation.getClientProperty(ActionUtil.SECONDARY_TEXT)).isNull()
    assertThat(presentation.getClientProperty(ActionUtil.SECONDARY_ICON)).isNull()
  }

  @Test
  fun `run mode can render Junie with new badge`() {
    val junie = bundledAgentByKey("junie")
    val presentation = Presentation()

    TerminalAgentsSelectorPresentationUtil.applyToPresentation(presentation, junie, TerminalAgentMode.RUN, showNewBadge = true)

    assertThat(presentation.text).contains("Junie").contains("by JetBrains")
    assertThat(presentation.getClientProperty(ActionUtil.SECONDARY_TEXT)).isNull()
    assertThat(presentation.getClientProperty(ActionUtil.SECONDARY_ICON)).isEqualTo(AllIcons.General.New_badge)
  }

  private fun bundledAgentByKey(agentKey: String) =
    DefaultTerminalAgentProvider().getTerminalAgents().first { it.agentKey.key == agentKey }
}
