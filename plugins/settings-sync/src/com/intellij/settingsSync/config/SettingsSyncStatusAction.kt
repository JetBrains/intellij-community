package com.intellij.settingsSync.config

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.SettingsEntryPointAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.settingsSync.SettingsSyncBundle.message
import com.intellij.settingsSync.SettingsSyncSettings
import com.intellij.settingsSync.SettingsSyncStatusTracker
import com.intellij.settingsSync.auth.SettingsSyncAuthService
import com.intellij.settingsSync.isSettingsSyncEnabledByKey
import com.intellij.ui.BadgeIconSupplier
import icons.SettingsSyncIcons
import javax.swing.Icon

class SettingsSyncStatusAction : SettingsSyncOpenSettingsAction(message("title.settings.sync")), SettingsSyncStatusTracker.Listener {

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

  init {
    SettingsSyncStatusTracker.getInstance().addListener(this)
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

  class IconCustomizer : SettingsEntryPointAction.IconCustomizer {
    override fun getCustomIcon(supplier: BadgeIconSupplier): Icon? {
      return if (getStatus() == SyncStatus.FAILED) {
        supplier.getErrorIcon(true)
      }
      else null
    }

  }

  override fun syncStatusChanged() {
    SettingsEntryPointAction.updateState()
  }

}