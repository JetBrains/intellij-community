package com.intellij.terminal.frontend.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableWithId
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.terminal.TERMINAL_CONFIGURABLE_ID
import java.util.function.Predicate

internal class TerminalSettingsAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    // Match the Terminal configurable by ID.
    // Can't use matching by configurable class name because actual configurable can be wrapped in case of Remote Dev.
    ShowSettingsUtil.getInstance().showSettingsDialog(project, Predicate { configurable: Configurable ->
      configurable is ConfigurableWithId && configurable.getId() == TERMINAL_CONFIGURABLE_ID
    }, null)
  }
}