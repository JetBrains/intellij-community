package com.intellij.settingsSync.config

import com.intellij.codeInsight.hint.HintUtil
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.settingsSync.*
import com.intellij.settingsSync.SettingsSyncBundle.message
import com.intellij.settingsSync.auth.SettingsSyncAuthService
import com.intellij.ui.layout.*
import org.jetbrains.annotations.Nls
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel

internal class SettingsSyncConfigurable : BoundConfigurable(message("title.settings.sync")), SettingsSyncEnabler.Listener {

  private lateinit var configPanel: DialogPanel
  private lateinit var enableButton: CellBuilder<JButton>

  private val syncEnabler = SettingsSyncEnabler()

  init {
    syncEnabler.addListener(this)
  }

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
      SettingsSyncSettings.getInstance().addListener(object : SettingsSyncEnabledStateListener {
        override fun enabledStateChanged(syncEnabled: Boolean) {
          listener(invoke())
        }
      }, disposable!!)
    }

    override fun invoke() = SettingsSyncSettings.getInstance().syncEnabled

  }

  inner class SyncEnablerRunning : ComponentPredicate() {
    private var isRunning = false

    override fun addListener(listener: (Boolean) -> Unit) {
      syncEnabler.addListener(object : SettingsSyncEnabler.Listener {
        override fun serverRequestStarted() {
          updateRunning(listener, true)
        }

        override fun serverRequestFinished() {
          updateRunning(listener, false)
        }
      })
    }

    private fun updateRunning(listener: (Boolean) -> Unit, isRunning: Boolean) {
      this.isRunning = isRunning
      listener(invoke())
    }

    override fun invoke(): Boolean = isRunning
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
          enableButton = button(message("config.button.enable")) {
            syncEnabler.checkServerState()
          }.visibleIf(LoggedInPredicate().and(EnabledPredicate().not())).enableIf(SyncEnablerRunning().not())
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

  override fun serverStateCheckFinished(state: ServerState) {
    when (state) {
      ServerState.FileNotExists, ServerState.UpdateNeeded -> showEnableSyncDialog(false)
      ServerState.UpToDate -> showEnableSyncDialog(true)
      is ServerState.Error -> {
        if (state != SettingsSyncEnabler.State.CANCELLED) {
          showError(enableButton.component, message("notification.title.update.error"), state.message)
        }
      }
    }
  }

  override fun updateFromServerFinished(result: UpdateResult) {
    when (result) {
      is UpdateResult.Success -> {
        SettingsSyncSettings.getInstance().syncEnabled = true
      }
      UpdateResult.NoFileOnServer -> {
        showError(enableButton.component, message("notification.title.update.error"), message("notification.title.update.no.such.file"))
      }
      is UpdateResult.Error -> {
        showError(enableButton.component, message("notification.title.update.error"), result.message)
      }
    }
  }

  private fun showEnableSyncDialog(remoteSettingsFound: Boolean) {
    EnableSettingsSyncDialog.showAndGetResult(configPanel, remoteSettingsFound)?.let {
      reset()
      when (it) {
        EnableSettingsSyncDialog.Result.ENABLE_SYNC -> SettingsSyncSettings.getInstance().syncEnabled = true
        EnableSettingsSyncDialog.Result.GET_FROM_SERVER -> syncEnabler.getSettingsFromServer()
        EnableSettingsSyncDialog.Result.PUSH_LOCAL -> {
          syncEnabler.pushSettingsToServer()
          SettingsSyncSettings.getInstance().syncEnabled = true
        }
      }
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

  private fun showError(component: JComponent, message: @Nls String, details: @Nls String) {
    val builder = JBPopupFactory.getInstance().createBalloonBuilder(JLabel(details))
    val balloon = builder.setTitle(message)
      .setFillColor(HintUtil.getErrorColor())
      .setDisposable(disposable!!)
      .createBalloon()
    balloon.showInCenterOf(component)
  }

}

class SettingsSyncConfigurableProvider: ConfigurableProvider() {
  override fun createConfigurable(): Configurable = SettingsSyncConfigurable()

  override fun canCreateConfigurable() = isSettingsSyncEnabledByKey()
}