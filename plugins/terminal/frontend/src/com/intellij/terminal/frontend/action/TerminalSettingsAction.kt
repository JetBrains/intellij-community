package com.intellij.terminal.frontend.action

import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.terminal.TerminalUtil

internal class TerminalSettingsAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    ShowSettingsUtilImpl.showSettingsDialog(project, TerminalUtil.TERMINAL_CONFIGURABLE_ID, null)
  }
}