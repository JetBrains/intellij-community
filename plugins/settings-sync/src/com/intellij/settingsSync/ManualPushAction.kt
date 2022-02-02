package com.intellij.settingsSync

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction

class ManualPushAction : DumbAwareAction() {

  override fun actionPerformed(e: AnActionEvent) {
    ApplicationManager.getApplication().messageBus.syncPublisher(SETTINGS_CHANGED_TOPIC).settingChanged(SyncSettingsEvent.MustPushRequest)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = isSettingsSyncEnabledByKey() && SettingsSyncMain.isAvailable() && isSettingsSyncEnabledInSettings()
  }
}