package com.intellij.settingsSync.config

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.settingsSync.SettingsSyncBundle.message
import com.intellij.settingsSync.SettingsSyncLocalStateHolder
import com.intellij.settingsSync.SettingsSyncState
import com.intellij.settingsSync.SettingsSyncStateHolder
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent

internal class EnableSettingsSyncDialog(
  parent: JComponent,
  remoteSettings: SettingsSyncState?,
  remoteSyncScopeSettings: SettingsSyncLocalStateHolder?,
) : DialogWrapper(parent, false) {

  private var dialogResult: Result? = null
  val syncSettings: SettingsSyncState = remoteSettings ?: SettingsSyncStateHolder()
  private val syncScopeSettings = remoteSyncScopeSettings ?: SettingsSyncLocalStateHolder()
  private val remoteSettingsExist: Boolean = remoteSettings != null

  init {
    title = message("title.settings.sync")
    init()
  }

  enum class Result {
    PUSH_LOCAL,
    GET_FROM_SERVER
  }

  override fun createCenterPanel(): JComponent {
    return  SettingsSyncPanelFactory.createCombinedSyncSettingsPanel(
      message("enable.dialog.select.what.to.sync"),
      syncSettings,
      syncScopeSettings,
    ).also { it.reset() }
  }

  override fun createActions(): Array<Action> =
    if (remoteSettingsExist) arrayOf(cancelAction, SyncLocalSettingsAction(), GetSettingsFromAccountAction())
    else {
      val enableSyncAction = EnableSyncAction()
      enableSyncAction.putValue(DEFAULT_ACTION, true)
      arrayOf(cancelAction, enableSyncAction)
    }

  inner class EnableSyncAction : AbstractAction(message("enable.dialog.enable.sync.action")) {
    override fun actionPerformed(e: ActionEvent?) {
      applyAndClose(Result.PUSH_LOCAL)
    }
  }

  inner class SyncLocalSettingsAction : AbstractAction(message("enable.dialog.sync.local.settings")) {
    override fun actionPerformed(e: ActionEvent?) {
      applyAndClose(Result.PUSH_LOCAL)
    }
  }

  inner class GetSettingsFromAccountAction : AbstractAction(message("enable.dialog.get.settings.from.account")) {
    override fun actionPerformed(e: ActionEvent?) {
      applyAndClose(Result.GET_FROM_SERVER)
    }
  }

  private fun applyAndClose(result: Result) {
    applyFields()
    dialogResult = result
    close(0, true)
  }

  fun getResult(): Result? = dialogResult
}