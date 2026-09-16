// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.action

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.terminal.frontend.session.StateAwareTerminalSession
import com.intellij.terminal.frontend.session.ghostty.GhosttyTerminalSession
import com.intellij.terminal.frontend.session.jediterm.JediTerminalSession
import com.intellij.terminal.frontend.toolwindow.getTerminalTab
import com.intellij.ui.JBColor
import com.intellij.ui.TextIcon
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.terminal.util.getNow

private val GHOSTTY_BADGE_BACKGROUND = JBColor(0xDCCBFB, 0x583D7A)
private val JEDITERM_BADGE_BACKGROUND = JBColor(0xFAD2B6, 0x614438)

/**
 * Internal-mode diagnostic: shows which emulator backs the selected terminal tab.
 * Visibility is toggled by [TerminalEmulatorBadgeToggleAction].
 */
internal class TerminalEmulatorBadgeAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  @Suppress("TestOnlyProblems") // Deliberate: only way to identify the emulator without a dedicated API
  override fun update(e: AnActionEvent) {
    val badgeIcon = if (ApplicationManager.getApplication().isInternal && TerminalEmulatorBadgeState.isVisible) {
      val session = e.getData(PlatformDataKeys.TOOL_WINDOW_CONTENT_MANAGER)
        ?.selectedContent
        ?.getTerminalTab()
        ?.view
        ?.sessionDeferred
        ?.getNow()
      val rawSession = (session as? StateAwareTerminalSession)?.delegate ?: session
      when (rawSession) {
        is GhosttyTerminalSession -> createBadgeIcon("Ghostty", GHOSTTY_BADGE_BACKGROUND)
        is JediTerminalSession -> createBadgeIcon("JediTerm", JEDITERM_BADGE_BACKGROUND)
        else -> null
      }
    }
    else null

    e.presentation.isEnabledAndVisible = badgeIcon != null
    if (badgeIcon != null) {
      e.presentation.icon = badgeIcon
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    // A static indicator, nothing to do on click.
  }

  private fun createBadgeIcon(text: String, background: JBColor): TextIcon {
    return TextIcon(" $text ", UIUtil.getLabelForeground(), background, 4).apply {
      font = JBFont.label()
    }
  }
}

internal object TerminalEmulatorBadgeState {
  private const val KEY = "Terminal.EmulatorBadge.visible"

  var isVisible: Boolean
    get() = PropertiesComponent.getInstance().getBoolean(KEY, false)
    set(value) = PropertiesComponent.getInstance().setValue(KEY, value, false)
}

internal class TerminalEmulatorBadgeToggleAction : DumbAwareToggleAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isSelected(e: AnActionEvent): Boolean = TerminalEmulatorBadgeState.isVisible

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    TerminalEmulatorBadgeState.isVisible = state
  }
}