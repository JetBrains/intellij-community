package com.intellij.settingsSync.config

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.settingsSync.SettingsSyncBundle.message
import com.intellij.settingsSync.SettingsSyncSettings
import com.intellij.settingsSync.SettingsSyncStatusTracker
import com.intellij.settingsSync.auth.SettingsSyncAuthService
import com.intellij.settingsSync.isSettingsSyncEnabledByKey
import icons.SettingsSyncIcons

class SettingsSyncStatusAction : DumbAwareAction(message("title.settings.sync")) {

  private enum class SyncStatus {ON, OFF, FAILED}

  companion object {
    private fun getStatus() : SyncStatus {
      if (SettingsSyncSettings.getInstance().syncEnabled &&
          SettingsSyncAuthService.getInstance().isLoggedIn()) {
        return if (SettingsSyncStatusTracker.getInstance().isSyncSuccessful()) SyncStatus.ON
        else SyncStatus.FAILED
      }
      else
        return SyncStatus.OFF
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    ShowSettingsUtil.getInstance().showSettingsDialog(e.project, SettingsSyncConfigurable::class.java)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val p = e.presentation
    if (!isSettingsSyncEnabledByKey()) {
      p.isEnabledAndVisible = false
      return
    }
    when (getStatus()) {
      SyncStatus.ON -> {
        p.icon = SettingsSyncIcons.StatusEnabled
        @Suppress("DialogTitleCapitalization") // we use "is", not "Is
        p.text = message("status.action.settings.sync.is.on")
      }
      SyncStatus.OFF -> {
        p.icon = SettingsSyncIcons.StatusDisabled
        @Suppress("DialogTitleCapitalization") // we use "is", not "Is
        p.text = message("status.action.settings.sync.is.off")
      }
      SyncStatus.FAILED -> {
        p.icon = AllIcons.General.Error
        p.text = message("status.action.settings.sync.failed")
      }
    }
  }

}