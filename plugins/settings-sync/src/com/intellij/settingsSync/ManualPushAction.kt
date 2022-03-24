package com.intellij.settingsSync

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class ManualPushAction : DumbAwareAction() {

  override fun actionPerformed(e: AnActionEvent) {
    SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.MustPushRequest)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = isSettingsSyncEnabledByKey() && SettingsSyncMain.isAvailable() && isSettingsSyncEnabledInSettings()
  }
}