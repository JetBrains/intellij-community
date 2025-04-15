package com.intellij.terminal.frontend.action

import com.intellij.configurationStore.saveSettingsForRemoteDevelopment
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.ui.ExperimentalUI
import com.intellij.util.application
import org.jetbrains.plugins.terminal.TerminalEngine
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.TerminalUtil

internal sealed class TerminalChangeEngineAction(private val engine: TerminalEngine) : DumbAwareToggleAction() {
  init {
    templatePresentation.text = engine.presentableName
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return TerminalOptionsProvider.instance.terminalEngine == engine
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (state) {
      TerminalOptionsProvider.instance.terminalEngine = engine
      // Call save manually, because otherwise this change will be synced to backend only at some time later.
      saveSettingsForRemoteDevelopment(application)

      TerminalToolWindowManager.getInstance(e.project!!).createNewSession()
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)

    e.presentation.isEnabledAndVisible = e.project != null &&
                                         ExperimentalUI.isNewUI() &&
                                         (engine != TerminalEngine.NEW_TERMINAL ||
                                          TerminalUtil.isGenOneTerminalOptionVisible() == true ||
                                          // Normally, New Terminal can't be enabled if 'getGenOneTerminalVisibilityValue' is false.
                                          // But if it is enabled for some reason (for example, the corresponding registry key was switched manually),
                                          // show this option as well to avoid strange behavior when nothing is selected in the popup.
                                          TerminalOptionsProvider.instance.terminalEngine == TerminalEngine.NEW_TERMINAL)
    e.presentation.keepPopupOnPerform = KeepPopupOnPerform.IfRequested

    if (engine == TerminalEngine.REWORKED) {
      e.presentation.putClientProperty(ActionUtil.SECONDARY_ICON, AllIcons.General.Beta)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

internal class TerminalReworkedEngineAction : TerminalChangeEngineAction(TerminalEngine.REWORKED)

internal class TerminalClassicEngineAction : TerminalChangeEngineAction(TerminalEngine.CLASSIC)

internal class TerminalNewTerminalEngineAction : TerminalChangeEngineAction(TerminalEngine.NEW_TERMINAL)