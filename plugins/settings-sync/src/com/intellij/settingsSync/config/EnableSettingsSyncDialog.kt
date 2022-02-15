package com.intellij.settingsSync.config

import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.settingsSync.SettingsSyncBundle.message
import org.jetbrains.annotations.Nls
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent

internal class EnableSettingsSyncDialog(parent: JComponent, val settingsFound: Boolean) : DialogWrapper(parent, false) {

  private lateinit var configPanel: DialogPanel
  private var isConfirmed = false

  init {
    title = message("title.settings.sync")
    init()
  }

  companion object Result {
    const val ENABLE_SYNC = 0
    const val PUSH_LOCAL = 100
    const val GET_FROM_SERVER = 101
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
      applyAndClose(ENABLE_SYNC)
    }
  }

  inner class SyncLocalSettingsAction : AbstractAction(message("enable.dialog.sync.local.settings")) {
    override fun actionPerformed(e: ActionEvent?) {
      applyAndClose(PUSH_LOCAL)
    }
  }

  inner class GetSettingsFromAccountAction : AbstractAction(message("enable.dialog.get.settings.from.account")) {
    override fun actionPerformed(e: ActionEvent?) {
      applyAndClose(GET_FROM_SERVER)
    }
  }

  private fun applyAndClose(exitCode: Int) {
    configPanel.apply()
    isConfirmed = true
    close(exitCode, true)
  }

  fun isConfirmed() : Boolean = this.isConfirmed
}