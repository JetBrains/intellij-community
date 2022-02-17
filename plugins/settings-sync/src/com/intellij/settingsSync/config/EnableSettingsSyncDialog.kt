package com.intellij.settingsSync.config

import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.settingsSync.SettingsSyncBundle.message
import org.jetbrains.annotations.Nls
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent

internal class EnableSettingsSyncDialog
  private constructor(parent: JComponent, private val remoteSettingsFound: Boolean) : DialogWrapper(parent, false) {

  private lateinit var configPanel: DialogPanel
  private var dialogResult: Result? = null

  init {
    title = message("title.settings.sync")
    init()
  }

  enum class Result {
    ENABLE_SYNC,
    PUSH_LOCAL,
    GET_FROM_SERVER
  }

  companion object {
    fun showAndGetResult(parent: JComponent, remoteSettingsFound: Boolean) : Result? {
      val dialog = EnableSettingsSyncDialog(parent, remoteSettingsFound)
      dialog.show()
      return dialog.getResult()
    }
  }

  override fun createCenterPanel(): JComponent {
    configPanel = SettingsSyncPanelFactory.createPanel(getHeader())
    configPanel.reset()
    return configPanel
  }

  private fun getHeader(): @Nls String {
    return (if (remoteSettingsFound) message("enable.dialog.settings.found") + " " else "") + message("enable.dialog.select.what.to.sync")
  }

  override fun createActions(): Array<Action> =
    if (remoteSettingsFound) arrayOf(cancelAction, SyncLocalSettingsAction(), GetSettingsFromAccountAction())
    else arrayOf(cancelAction, EnableSyncAction())

  inner class EnableSyncAction : AbstractAction(message("enable.dialog.enable.sync.action")) {
    override fun actionPerformed(e: ActionEvent?) {
      applyAndClose(Result.ENABLE_SYNC)
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
    configPanel.apply()
    dialogResult = result
    close(0, true)
  }

  fun getResult(): Result? = dialogResult
}