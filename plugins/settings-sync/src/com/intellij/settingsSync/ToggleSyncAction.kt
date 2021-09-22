package com.intellij.settingsSync

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware

class ToggleSyncAction : ToggleAction(), DumbAware {
  override fun isSelected(e: AnActionEvent): Boolean {
    return service<SettingsSyncSettings>().syncEnabled
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    service<SettingsSyncSettings>().syncEnabled = state
  }
}