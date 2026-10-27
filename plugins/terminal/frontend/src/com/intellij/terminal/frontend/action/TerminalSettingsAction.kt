package com.intellij.terminal.frontend.action

import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.TerminalUtil

internal class TerminalSettingsAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)

    e.presentation.isEnabledAndVisible = e.place != ActionPlaces.ACTION_SEARCH
    if (e.place == ActionPlaces.ACTION_SEARCH) { // still can be shown as disabled
      e.presentation.text = TerminalBundle.message("action.Terminal.Settings.text.full")
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    ShowSettingsUtilImpl.showSettingsDialog(project, TerminalUtil.TERMINAL_CONFIGURABLE_ID, null)
  }
}