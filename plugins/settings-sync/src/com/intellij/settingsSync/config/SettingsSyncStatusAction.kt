package com.intellij.settingsSync.config

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.SettingsEntryPointAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.settingsSync.SettingsSyncBundle.message
import com.intellij.settingsSync.SettingsSyncSettings
import com.intellij.settingsSync.SettingsSyncStatusTracker
import com.intellij.settingsSync.auth.SettingsSyncAuthService
import com.intellij.settingsSync.communicator.RemoteCommunicatorHolder
import com.intellij.settingsSync.isSettingsSyncEnabledByKey
import com.intellij.ui.BadgeIconSupplier
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.SettingsSyncIcons
import org.jetbrains.annotations.Nls
import javax.swing.Icon

private enum class SyncStatus {ON, OFF, FAILED}

private fun getStatus() : SyncStatus {
  if (SettingsSyncSettings.getInstance().syncEnabled &&
      RemoteCommunicatorHolder.getAuthService().isLoggedIn()) {
    return if (SettingsSyncStatusTracker.getInstance().isSyncSuccessful()) SyncStatus.ON
    else SyncStatus.FAILED
  }
  else
    return SyncStatus.OFF
}

internal class SettingsSyncStatusAction : SettingsSyncOpenSettingsAction(),
                                          SettingsEntryPointAction.NoDots,
                                          SettingsSyncStatusTracker.Listener {

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
    val status = getStatus()
    when (status) {
      SyncStatus.ON ->
        p.icon = SettingsSyncIcons.StatusEnabled
      SyncStatus.OFF ->
        p.icon = SettingsSyncIcons.StatusDisabled
      SyncStatus.FAILED ->
        p.icon = AllIcons.General.Error
    }
    p.text = getStyledStatus(status)
  }

  private fun getStyledStatus(status: SyncStatus): @Nls String {
    val builder = StringBuilder()
    builder.append("<html>")
      .append(message("status.action.settings.sync")).append(" ")
      .append("<font color='#")
    val hexColor = UIUtil.colorToHex(JBUI.CurrentTheme.Popup.mnemonicForeground())
    builder.append(hexColor).append("'>")
    when (status) {
      SyncStatus.ON -> builder.append(message("status.action.settings.sync.is.on"))
      SyncStatus.OFF -> builder.append(message("status.action.settings.sync.is.off"))
      SyncStatus.FAILED -> builder.append(message("status.action.settings.sync.failed"))
    }
    builder
      .append("</font>")
    return "$builder"
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