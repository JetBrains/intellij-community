package com.intellij.settingsSync.config

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.settingsSync.SettingsSyncBundle.message
import com.intellij.settingsSync.SettingsSyncSettings
import com.intellij.settingsSync.auth.SettingsSyncAuthService
import com.intellij.settingsSync.isSettingsSyncEnabledByKey

class SettingsSyncStatusAction : AnAction(message("title.settings.sync")) {
  override fun actionPerformed(e: AnActionEvent) {
    ShowSettingsUtil.getInstance().showSettingsDialog(null, SettingsSyncConfigurable::class.java)
  }

  override fun update(e: AnActionEvent) {
    val p = e.presentation
    if (!isSettingsSyncEnabledByKey()) {
      p.isEnabledAndVisible = false
      return
    }
    when(getStatus()) {
      SyncStatus.ON -> {
        p.icon = AllIcons.General.InspectionsOK // TODO<rv>: Change icon
        @Suppress("DialogTitleCapitalization") // we use "is", not "Is
        p.text = message("status.action.settings.sync.is.on")
      }
      SyncStatus.OFF -> {
        p.icon = AllIcons.Actions.Cancel // TODO<rv>: Change icon
        @Suppress("DialogTitleCapitalization") // we use "is", not "Is
        p.text = message("status.action.settings.sync.is.off")
      }
      SyncStatus.FAILED -> {
        p.icon = AllIcons.General.Error
        p.text = message("status.action.settings.sync.failed")
      }
    }
  }

  private enum class SyncStatus {ON, OFF, FAILED}

  private fun getStatus() : SyncStatus {
    // TODO<rv>: Support FAILED status
    return if (SettingsSyncSettings.getInstance().syncEnabled &&
        SettingsSyncAuthService.getInstance().isLoggedIn()) SyncStatus.ON
    else SyncStatus.OFF
  }
}