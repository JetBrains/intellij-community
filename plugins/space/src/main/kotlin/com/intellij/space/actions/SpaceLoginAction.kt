package com.intellij.space.actions

import com.intellij.space.components.space
import com.intellij.space.settings.SpaceSettingsPanel
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class SpaceLoginAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = space.workspace.value == null
    SpaceActionUtils.showIconInActionSearch(e)
  }

  override fun actionPerformed(e: AnActionEvent) {
    SpaceSettingsPanel.openSettings(e.project)
  }
}
