package com.intellij.settingsSync.config

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.settingsSync.SettingsSyncBundle.message
import com.intellij.settingsSync.SettingsSyncSettings
import com.intellij.settingsSync.auth.SettingsSyncAuthService
import com.intellij.settingsSync.isSettingsSyncEnabledByKey
import com.intellij.ui.layout.*
import javax.swing.JCheckBox

internal class SettingsSyncConfigurable : BoundConfigurable(message("title.settings.sync")) {

  private lateinit var configPanel: DialogPanel

  inner class LoggedInPredicate : ComponentPredicate() {
    override fun addListener(listener: (Boolean) -> Unit) =
      SettingsSyncAuthService.getInstance().addListener(object : SettingsSyncAuthService.Listener {
        override fun stateChanged() {
          listener(invoke())
        }
      }, disposable!!)

    override fun invoke() = SettingsSyncAuthService.getInstance().isLoggedIn()
  }

  inner class EnabledPredicate : ComponentPredicate() {
    override fun addListener(listener: (Boolean) -> Unit) {
      SettingsSyncSettings.getInstance().addListener(object : SettingsSyncSettings.Listener {
        override fun settingsChanged() {
          listener(invoke())
        }
      }, disposable!!)
    }

    override fun invoke() = SettingsSyncSettings.getInstance().syncEnabled

  }

  override fun createPanel(): DialogPanel {
    val categoriesPanel = SettingsSyncPanelFactory.createPanel(message("configurable.what.to.sync.label"))
    configPanel = panel {
      val isSyncEnabled = LoggedInPredicate().and(EnabledPredicate())
      row {
        cell {
          label(message("sync.status"))
          @Suppress("DialogTitleCapitalization") // Partial content
          label(message("sync.status.disabled")).visibleIf(isSyncEnabled.not())
          @Suppress("DialogTitleCapitalization") // Partial content
          label(message("sync.status.enabled")).visibleIf(isSyncEnabled)
          label(message("sync.status.login.message")).visibleIf(LoggedInPredicate().not())
          // TODO<rv>: Add last sync time and "Sync now" link
        }
      }
      row {
        comment(message("settings.sync.info.message"), 80)
          .visibleIf(isSyncEnabled.not())
      }
      row {
        cell {
          label("") // The first component must be always visible
          button(message("config.button.login")) {
            SettingsSyncAuthService.getInstance().login()
          }.visibleIf(LoggedInPredicate().not())
          button(message("config.button.enable")) {
            enableSync()
          }.visibleIf(LoggedInPredicate().and(EnabledPredicate().not()))
          button(message("config.button.disable")) {LoggedInPredicate().and(EnabledPredicate())
            disableSync()
          }.visibleIf(isSyncEnabled)
        }
      }
      row {
        component(categoriesPanel)
          .visibleIf(LoggedInPredicate().and(EnabledPredicate()))
          .onApply { categoriesPanel.apply() }
          .onReset { categoriesPanel.reset() }
          .onIsModified { categoriesPanel.isModified() }
      }
    }
    return configPanel
  }

  private fun enableSync() {
    val dialog = EnableSettingsSyncDialog(configPanel, false)
    if (dialog.showAndGet()) {
      reset()
    }
  }

  companion object DisableResult {
    const val RESULT_CANCEL = 0
    const val RESULT_REMOVE_DATA_AND_DISABLE = 1
    const val RESULT_DISABLE = 2
  }

  private fun disableSync() {
    @Suppress("DialogTitleCapitalization")
    val result = Messages.showCheckboxMessageDialog( // TODO<rv>: Use AlertMessage instead
      message("disable.dialog.text"),
      message("disable.dialog.title"),
      arrayOf(Messages.getCancelButton(), message("disable.dialog.disable.button")),
      message("disable.dialog.remove.data.box"),
      false,
      1,
      1,
      Messages.getInformationIcon()
    ) { index: Int, checkbox: JCheckBox ->
      when {
        index == 0 -> RESULT_CANCEL
        checkbox.isSelected -> RESULT_REMOVE_DATA_AND_DISABLE
        else -> RESULT_DISABLE
      }
    }

    if (result != RESULT_CANCEL) {
      SettingsSyncSettings.getInstance().syncEnabled = false
    }
  }

}

class SettingsSyncConfigurableProvider: ConfigurableProvider() {
  override fun createConfigurable(): Configurable = SettingsSyncConfigurable()

  override fun canCreateConfigurable() = isSettingsSyncEnabledByKey()
}