package com.intellij.settingsSync.config

import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.settingsSync.SettingsSyncBundle.message
import com.intellij.settingsSync.SettingsSyncSettings
import org.jetbrains.annotations.Nls
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent

internal class EnableSettingsSyncDialog(parent: JComponent, val settingsFound: Boolean) : DialogWrapper(parent, false) {

  private lateinit var configPanel : DialogPanel

  init {
    title = message("title.settings.sync")
    init()
  }

  override fun createCenterPanel(): JComponent {
    configPanel = SettingsSyncPanelFactory.createPanel(getHeader())
    configPanel.reset()
    return configPanel
  }

  private fun getHeader(): @Nls String {
    return (if (settingsFound) message("enable.dialog.settings.found") + " " else "") + message("enable.dialog.select.what.to.sync")
  }

  override fun createActions(): Array<Action> =
    if (settingsFound) arrayOf(cancelAction, SyncLocalSettingsAction(), GetSettingsFromAccountAction())
    else arrayOf(cancelAction, EnableSyncAction())

  inner class EnableSyncAction : AbstractAction(message("enable.dialog.enable.sync.action")) {
    override fun actionPerformed(e: ActionEvent?) {
      configPanel.apply()
      SettingsSyncSettings.getInstance().syncEnabled = true
      close(0, true)
    }
  }

  inner class SyncLocalSettingsAction : AbstractAction(message("enable.dialog.sync.local.settings")) {
    override fun actionPerformed(e: ActionEvent?) {
      TODO("Not yet implemented")
    }
  }

  inner class GetSettingsFromAccountAction : AbstractAction(message("enable.dialog.get.settings.from.account")) {
    override fun actionPerformed(e: ActionEvent?) {
      TODO("Not yet implemented")
    }
  }
}