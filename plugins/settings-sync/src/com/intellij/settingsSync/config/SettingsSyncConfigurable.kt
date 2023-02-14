package com.intellij.settingsSync.config

import com.intellij.configurationStore.StateStorageManagerImpl
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.settingsSync.*
import com.intellij.settingsSync.SettingsSyncBundle.message
import com.intellij.settingsSync.UpdateResult.*
import com.intellij.settingsSync.auth.SettingsSyncAuthService
import com.intellij.ui.JBColor
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.layout.and
import com.intellij.ui.layout.not
import com.intellij.util.text.DateFormatUtil
import org.jetbrains.annotations.Nls
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel

internal class SettingsSyncConfigurable : BoundConfigurable(message("title.settings.sync")),
                                          SettingsSyncEnabler.Listener,
                                          SettingsSyncStatusTracker.Listener {

  private lateinit var configPanel: DialogPanel
  private lateinit var enableButton: Cell<JButton>
  private lateinit var statusLabel: JLabel

  private val syncEnabler = SettingsSyncEnabler()

  init {
    syncEnabler.addListener(this)
    SettingsSyncStatusTracker.getInstance().addListener(this)
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
      SettingsSyncEvents.getInstance().addEnabledStateChangeListener(object : SettingsSyncEnabledStateListener {
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
    val authService = SettingsSyncAuthService.getInstance()
    configPanel = panel {
      val isSyncEnabled = LoggedInPredicate().and(EnabledPredicate())
      if (settingsRepositoryIsEnabled()) {
        row {
          label(message("settings.warning.sync.cannot.be.enabled.label")).applyToComponent {
            icon = AllIcons.General.Warning
          }
          bottomGap(BottomGap.MEDIUM)
        }
      }

      row {
        val statusCell = label("")
        statusCell
          .visibleIf(LoggedInPredicate())
          .enabled(!settingsRepositoryIsEnabled())
        statusLabel = statusCell.component
        updateStatusInfo()
        label(message("sync.status.login.message"))
          .visibleIf(LoggedInPredicate().not())
          .enabled(!settingsRepositoryIsEnabled())
      }
      row {
        comment(message("settings.sync.info.message"), 80)
          .visibleIf(isSyncEnabled.not())
      }
      row {
        button(message("config.button.login")) {
          authService.login()
        }.visibleIf(LoggedInPredicate().not())
         .enabled(authService.isLoginAvailable() && !settingsRepositoryIsEnabled())

        label(message("error.label.login.not.available")).component.apply {
          isVisible = !authService.isLoginAvailable()
          icon = AllIcons.General.Error
          foreground = JBColor.red
        }
        enableButton = button(message("config.button.enable")) {
          syncEnabler.checkServerState()
        }.visibleIf(LoggedInPredicate().and(EnabledPredicate().not()))
         .enabledIf(SyncEnablerRunning().not())
         .enabled(!settingsRepositoryIsEnabled())

        button(message("config.button.disable")) {
          LoggedInPredicate().and(EnabledPredicate())
          disableSync()
        }.visibleIf(isSyncEnabled)
        bottomGap(BottomGap.MEDIUM)
      }
      row {
        cell(categoriesPanel)
          .visibleIf(LoggedInPredicate().and(EnabledPredicate()))
          .onApply {
            categoriesPanel.apply()
            SettingsSyncEvents.getInstance().fireCategoriesChanged()
          }
          .onReset { categoriesPanel.reset() }
          .onIsModified { categoriesPanel.isModified() }
      }

      panel {
        row {
          topGap(TopGap.MEDIUM)
          label(message("settings.cross.product.sync"))
        }
        indent {
          buttonsGroup {
            row {
              radioButton(
                message("settings.cross.product.sync.choice.only.this.product", ApplicationNamesInfo.getInstance().fullProductName), false)
            }
            row {
              radioButton(message("settings.cross.product.sync.choice.all.products"), true)
            }
          }.bind({ SettingsSyncLocalSettings.getInstance().isCrossIdeSyncEnabled },
                 {
                   SettingsSyncLocalSettings.getInstance().isCrossIdeSyncEnabled = it

                   SettingsSyncEvents.getInstance().fireSettingsChanged(
                     SyncSettingsEvent.CrossIdeSyncStateChanged(SettingsSyncLocalSettings.getInstance().isCrossIdeSyncEnabled))
                 })
        }
      }.visibleIf(LoggedInPredicate().and(EnabledPredicate()))
    }
    SettingsSyncAuthService.getInstance().addListener(object : SettingsSyncAuthService.Listener {
      override fun stateChanged() {
        if (SettingsSyncAuthService.getInstance().isLoggedIn() && !SettingsSyncSettings.getInstance().syncEnabled) {
          syncEnabler.checkServerState()
        }
      }
    }, disposable!!)
    return configPanel
  }

  private fun settingsRepositoryIsEnabled(): Boolean {
    return !SettingsSyncSettings.getInstance().syncEnabled &&
           (ApplicationManager.getApplication().stateStore.storageManager as StateStorageManagerImpl).compoundStreamProvider.isExclusivelyEnabled
  }

  override fun serverStateCheckFinished(updateResult: UpdateResult) {
    when (updateResult) {
      NoFileOnServer, FileDeletedFromServer -> showEnableSyncDialog(false)
      is Success -> showEnableSyncDialog(true)
      is Error -> {
        if (updateResult != SettingsSyncEnabler.State.CANCELLED) {
          showError(message("notification.title.update.error"), updateResult.message)
        }
      }
    }
  }

  override fun updateFromServerFinished(result: UpdateResult) {
    when (result) {
      is Success -> {
        reset()
        SettingsSyncSettings.getInstance().syncEnabled = true
      }
      NoFileOnServer, FileDeletedFromServer -> {
        showError(message("notification.title.update.error"), message("notification.title.update.no.such.file"))
      }
      is Error -> {
        showError(message("notification.title.update.error"), result.message)
      }
    }
    updateStatusInfo()
  }

  private fun showEnableSyncDialog(remoteSettingsFound: Boolean) {
    val dialogResult = EnableSettingsSyncDialog.showAndGetResult(configPanel, remoteSettingsFound)
    if (dialogResult != null) {
      reset()
      when (dialogResult) {
        EnableSettingsSyncDialog.Result.GET_FROM_SERVER -> {
          syncEnabler.getSettingsFromServer()
          SettingsSyncEventsStatistics.ENABLED_MANUALLY.log(SettingsSyncEventsStatistics.EnabledMethod.GET_FROM_SERVER)
        }
        EnableSettingsSyncDialog.Result.PUSH_LOCAL -> {
          SettingsSyncSettings.getInstance().syncEnabled = true
          syncEnabler.pushSettingsToServer()
          if (remoteSettingsFound) {
            SettingsSyncEventsStatistics.ENABLED_MANUALLY.log(SettingsSyncEventsStatistics.EnabledMethod.PUSH_LOCAL)
          }
          else {
            SettingsSyncEventsStatistics.ENABLED_MANUALLY.log(SettingsSyncEventsStatistics.EnabledMethod.PUSH_LOCAL_WAS_ONLY_WAY)
          }
        }
      }
    }
    else {
      SettingsSyncEventsStatistics.ENABLED_MANUALLY.log(SettingsSyncEventsStatistics.EnabledMethod.CANCELED)
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
      if (index == 1) {
        if (checkbox.isSelected) RESULT_REMOVE_DATA_AND_DISABLE else RESULT_DISABLE
      }
      else {
        RESULT_CANCEL
      }
    }

    when (result) {
      RESULT_DISABLE -> {
        SettingsSyncSettings.getInstance().syncEnabled = false
        updateStatusInfo()
        SettingsSyncEventsStatistics.DISABLED_MANUALLY.log(SettingsSyncEventsStatistics.ManualDisableMethod.DISABLED_ONLY)
      }
      RESULT_REMOVE_DATA_AND_DISABLE -> {
        disableAndRemoveData()
        SettingsSyncEventsStatistics.DISABLED_MANUALLY.log(
          SettingsSyncEventsStatistics.ManualDisableMethod.DISABLED_AND_REMOVED_DATA_FROM_SERVER)
      }
      RESULT_CANCEL -> {
        SettingsSyncEventsStatistics.DISABLED_MANUALLY.log(SettingsSyncEventsStatistics.ManualDisableMethod.CANCEL)
      }
    }
  }

  private fun disableAndRemoveData() {
    object : Task.Modal(null, message("disable.remove.data.title"), false) {
      override fun run(indicator: ProgressIndicator) {
        val cdl = CountDownLatch(1)
        SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.DeleteServerData { result ->
          cdl.countDown()
          when (result) {
            is DeleteServerDataResult.Error -> {
              runInEdt {
                showError(message("disable.remove.data.failure"), result.error)
              }
            }
            DeleteServerDataResult.Success -> {
              runInEdt {
                updateStatusInfo()
              }
            }
          }
        })
        cdl.await(1, TimeUnit.MINUTES)
      }
    }.queue()
  }

  private fun showError(message: @Nls String, details: @Nls String) {
    val messageBuilder = StringBuilder()
    messageBuilder.append(message("sync.status.failed"))
    statusLabel.icon = AllIcons.General.Error
    messageBuilder.append(' ').append("$message: $details")
    @Suppress("HardCodedStringLiteral")
    statusLabel.text = messageBuilder.toString()
  }

  private fun updateStatusInfo() {
    if (::statusLabel.isInitialized) {
      val messageBuilder = StringBuilder()
      statusLabel.icon = null
      if (SettingsSyncSettings.getInstance().syncEnabled) {
        val statusTracker = SettingsSyncStatusTracker.getInstance()
        if (statusTracker.isSyncSuccessful()) {
          messageBuilder
            .append(message("sync.status.enabled"))
          if (statusTracker.isSynced()) {
            messageBuilder
              .append(". ")
              .append(message("sync.status.last.sync.message", getReadableSyncTime(), getUserName()))
          }
        }
        else {
          messageBuilder.append(message("sync.status.failed"))
          statusLabel.icon = AllIcons.General.Error
          messageBuilder.append(' ').append(statusTracker.getErrorMessage())
        }
      }
      else {
        messageBuilder.append(message("sync.status.disabled"))
      }
      @Suppress("HardCodedStringLiteral") // The above strings are localized
      statusLabel.text = messageBuilder.toString()
    }
  }

  private fun getReadableSyncTime(): String {
    return DateFormatUtil.formatPrettyDateTime(SettingsSyncStatusTracker.getInstance().getLastSyncTime()).lowercase()
  }

  private fun getUserName(): String {
    return SettingsSyncAuthService.getInstance().getUserData()?.loginName ?: "?"
  }

  override fun syncStatusChanged() {
    updateStatusInfo()
  }

  override fun disposeUIResources() {
    super.disposeUIResources()
    SettingsSyncStatusTracker.getInstance().removeListener(this)
  }

  override fun getHelpTopic(): String = "cloud-config.plugin-dialog"
}

class SettingsSyncConfigurableProvider : ConfigurableProvider() {
  override fun createConfigurable(): Configurable = SettingsSyncConfigurable()

  override fun canCreateConfigurable() = isSettingsSyncEnabledByKey()
}