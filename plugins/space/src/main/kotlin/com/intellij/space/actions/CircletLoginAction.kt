package com.intellij.space.actions

import com.intellij.space.components.space
import com.intellij.space.settings.CircletSettingsPanel
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class CircletLoginAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = space.workspace.value == null
    CircletActionUtils.showIconInActionSearch(e)
  }

  override fun actionPerformed(e: AnActionEvent) {
    CircletSettingsPanel.openSettings(e.project)
  }
}
