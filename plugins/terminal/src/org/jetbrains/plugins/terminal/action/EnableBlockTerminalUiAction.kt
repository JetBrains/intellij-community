// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ExperimentalUI
import org.jetbrains.plugins.terminal.LocalBlockTerminalRunner
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.fus.BlockTerminalSwitchPlace
import org.jetbrains.plugins.terminal.fus.TerminalUsageTriggerCollector

class EnableBlockTerminalUiAction : DumbAwareToggleAction(TerminalBundle.messagePointer("action.Terminal.EnableNewUi.text")) {
  override fun isSelected(e: AnActionEvent): Boolean {
    return Registry.`is`(LocalBlockTerminalRunner.BLOCK_TERMINAL_REGISTRY)
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    Registry.get(LocalBlockTerminalRunner.BLOCK_TERMINAL_REGISTRY).setValue(state)
    val project = e.project!!
    TerminalUsageTriggerCollector.triggerBlockTerminalSwitched(project, state, BlockTerminalSwitchPlace.TOOLWINDOW_OPTIONS)
    TerminalToolWindowManager.getInstance(project).createNewSession()
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = e.project != null && ExperimentalUI.isNewUI()
    e.presentation.putClientProperty(ActionMenu.SECONDARY_ICON, AllIcons.General.Beta)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}