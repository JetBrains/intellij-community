package com.intellij.settingsSync.config


import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.ui.DialogPanel
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.settingsSync.*
import com.intellij.settingsSync.SettingsSyncBundle.message
import com.intellij.settingsSync.UpdateResult.*
import com.intellij.settingsSync.communicator.RemoteCommunicatorHolder
import com.intellij.settingsSync.communicator.SettingsSyncUserData
import com.intellij.settingsSync.config.SettingsSyncEnabler.State
import com.intellij.ui.components.DropDownLink
//import com.intellij.settingsSync.auth.SettingsSyncAuthService
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.layout.not
import javax.swing.JButton
import javax.swing.JLabel

internal class BackupNSyncConfigurable : BoundConfigurable(message("title.settings.sync")),
                                         SettingsSyncEnabler.Listener,
                                         SettingsSyncStatusTracker.Listener {
  companion object {
    private val LOG = logger<BackupNSyncConfigurable>()
  }

  private lateinit var configPanel: DialogPanel
  private lateinit var enableButton: JButton
  private lateinit var statusLabel: JLabel
  private lateinit var enabledDisabledLabel: JLabel
  private lateinit var userDropdown: DropDownLink<UserProviderHolder?>

  private val syncEnabler = SettingsSyncEnabler()
  private val enabledStatus = AtomicBooleanProperty(false)
  private val myUserProviderHolder = AtomicProperty<UserProviderHolder?>(null)
  private val enableSyncOption = AtomicProperty<EnableSettingsSyncDialog.Result>(EnableSettingsSyncDialog.Result.GET_FROM_SERVER)


  init {
    syncEnabler.addListener(this)
    SettingsSyncStatusTracker.getInstance().addListener(this)
  }

  override fun createPanel(): DialogPanel {
    val syncPanelHolder = SettingsSyncPanelHolder()
    val syncConfigPanel = syncPanelHolder.createCombinedSyncSettingsPanel(message("configurable.what.to.sync.label"),
                                                                          SettingsSyncSettings.getInstance(),
                                                                          SettingsSyncLocalSettings.getInstance())

    configPanel = panel {
      val enabledPredicate = EnabledPredicate()
      val initialProviderPredicate = InitialProviderPredicate()
      enabledStatus.set(SettingsSyncSettings.getInstance().syncEnabled)
      if (SettingsSyncLocalSettings.getInstance().userId != null && SettingsSyncLocalSettings.getInstance().providerCode != null) {
        val authService = RemoteCommunicatorHolder.getProvider(SettingsSyncLocalSettings.getInstance().providerCode!!)?.authService
        if (authService != null) {
          authService.getAvailableUserAccounts().find {
            it.id == SettingsSyncLocalSettings.getInstance().userId
          }?.apply {
            myUserProviderHolder.set(toUserProviderHolder(authService.providerName))
          }
        }
      }

      val enableMsg = message("config.button.enable")
      val disableMsg = message("config.button.disable")

      fun updateTexts() {
        if (enabledStatus.get()) {
          enabledDisabledLabel.text = "Enabled for:"
          enableButton.text = disableMsg
        } else {
          enabledDisabledLabel.text = "Disabled for:"
          enableButton.text = enableMsg
        }
      }

      row {
        label(message("settings.sync.info.message"))
      }.visibleIf(enabledPredicate.not())

      row {
        label(message("settings.sync.select.provider.message"))
      }.visibleIf(enabledPredicate.not())

      row {
        val availableProviders = RemoteCommunicatorHolder.getAvailableProviders()
        availableProviders.forEach { provider ->
          button(provider.authService.providerName) {
            runWithModalProgressBlocking(ModalTaskOwner.guess(), "Logging in...") {
              try {
                val userDataDeferred = provider.authService.login()
                val userData = userDataDeferred.await()
                if (userData != null) {
                  val remoteCommunicator = RemoteCommunicatorHolder.createRemoteCommunicator(provider, userData.id) ?: return@runWithModalProgressBlocking
                  if (checkServerState(syncPanelHolder, remoteCommunicator)) {
                    SettingsSyncLocalSettings.getInstance().userId = userData.id
                    SettingsSyncLocalSettings.getInstance().providerCode = userData.providerCode
                    myUserProviderHolder.set(UserProviderHolder(userData.id, userData, provider.authService.providerCode, provider.authService.providerName))
                    userDropdown.selectedItem = myUserProviderHolder.get()
                    SettingsSyncEvents.getInstance().fireLoginStateChanged()
                    enabledStatus.set(true)
                    updateTexts()
                    //syncConfigPanel.reset()
                  }
                }
                else {
                  LOG.info("Received empty user data from login")
                }
              }
              catch (ex: Throwable) {
                LOG.warn("Error during login", ex)
              }
            }
          }.applyToComponent {
            icon = provider.authService.icon
          }
        }
      }.visibleIf(initialProviderPredicate)

      row {
        val label = label("Disabled for: ")
        enabledDisabledLabel = label.component
        val providersMap = RemoteCommunicatorHolder.getAvailableProviders().map { it.providerCode to it }.toMap()
        val dropDownLinkCell = dropDownLink(myUserProviderHolder.get(),
                                            RemoteCommunicatorHolder.getAvailableUserAccounts().map {
                                          val providerName = providersMap[it.providerCode]?.authService?.providerName ?: "Unavailable"
                                          it.toUserProviderHolder(providerName)
                                        })
        userDropdown = dropDownLinkCell.component
      }.visibleIf(initialProviderPredicate.not())

      row {
        val enableButtonCell = button(enableMsg) {
          if (!enabledStatus.get()) {
            runWithModalProgressBlocking(ModalTaskOwner.component(configPanel), "Checking server status...") {
              if (checkServerState(syncPanelHolder)) {
                enabledStatus.set(true)
                updateTexts()
              }
            }
          } else {
            enabledStatus.set(false)
            updateTexts()
          }
        }
        enableButton = enableButtonCell.component
      }.visibleIf(initialProviderPredicate.not())


      // settings to sync
      group("Settings to sync") {
        row {
          cell(syncConfigPanel)
            .onReset(syncConfigPanel::reset)
            .onIsModified{
              enabledStatus.get() != SettingsSyncSettings.getInstance().syncEnabled || syncConfigPanel.isModified()
            }
            .onApply {
              with(SettingsSyncLocalSettings.getInstance()) {
                userId = myUserProviderHolder.get()?.userId
                providerCode = myUserProviderHolder.get()?.providerCode
              }
              if (enabledStatus.get()) {
                syncConfigPanel.apply()
              }
              if (SettingsSyncSettings.getInstance().syncEnabled != enabledStatus.get()) {
                SettingsSyncSettings.getInstance().syncEnabled = enabledStatus.get()
                if (enabledStatus.get()) {
                  if (enableSyncOption.get() == EnableSettingsSyncDialog.Result.GET_FROM_SERVER) {
                    syncEnabler.getSettingsFromServer()
                  }
                  else {
                    syncEnabler.pushSettingsToServer()
                  }
                }
              }
            }
        }
      }.visibleIf(enabledStatus)

      // apply necessary changes
      updateTexts()
    }
    return configPanel
  }

  private fun SettingsSyncUserData.toUserProviderHolder(providerName: String) =
    UserProviderHolder(id, this, providerCode, providerName)

  override fun syncStatusChanged() {
    // do nothing
  }

  private fun checkServerState(syncPanelHolder: SettingsSyncPanelHolder) : Boolean {
    val communicator = RemoteCommunicatorHolder.getRemoteCommunicator() ?: run {
      LOG.warn("communicator doesn't exist, skipping check")
      return false
    }
    return checkServerState(syncPanelHolder, communicator)
  }

  private fun checkServerState(syncPanelHolder: SettingsSyncPanelHolder, communicator: SettingsSyncRemoteCommunicator) : Boolean {
    val updateResult = try {
      communicator.receiveUpdates()
    }
    catch (ex: Exception) {
      LOG.warn(ex.message)
      State.CANCELLED
    }
    when (updateResult) {
      NoFileOnServer, FileDeletedFromServer -> {
        syncPanelHolder.setSyncSettings(null)
        syncPanelHolder.setSyncScopeSettings(null)
        enableSyncOption.set(EnableSettingsSyncDialog.Result.PUSH_LOCAL)
        return true
      }
      is Success -> {
        syncPanelHolder.setSyncSettings(updateResult.settingsSnapshot.getState())
        syncPanelHolder.setSyncScopeSettings(SettingsSyncLocalStateHolder(updateResult.isCrossIdeSyncEnabled))
        enableSyncOption.set(EnableSettingsSyncDialog.Result.GET_FROM_SERVER)
        return true
      }
      is Error -> {
        if (updateResult != SettingsSyncEnabler.State.CANCELLED) {
          //showError(message("notification.title.update.error"), state.message)
          return false
        }
      }
    }
    return false
  }

  /*
  private fun revealPanel(syncPanelHolder: SettingsSyncPanelHolder, remoteSettings: SettingsSyncState?, remoteSyncScopeSettings: SettingsSyncLocalStateHolder?) {

    if (dialogResult != null) {
      when (dialogResult) {
        EnableSettingsSyncDialog.Result.GET_FROM_SERVER -> {
          syncEnabler.getSettingsFromServer(dialog.syncSettings)

          SettingsSyncEventsStatistics.ENABLED_MANUALLY.log(SettingsSyncEventsStatistics.EnabledMethod.GET_FROM_SERVER)
        }

        EnableSettingsSyncDialog.Result.PUSH_LOCAL -> {
          SettingsSyncSettings.getInstance().syncEnabled = true

          syncEnabler.pushSettingsToServer()

          if (remoteSettings != null) {
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

    reset()
    configPanel.reset()
  }
  */



  inner class EnabledPredicate : ComponentPredicate() {
    override fun addListener(listener: (Boolean) -> Unit) =
      SettingsSyncEvents.getInstance().addListener(object : SettingsSyncEventListener {
        override fun enabledStateChanged(syncEnabled: Boolean) {
          listener(invoke())
          configPanel.reset()
        }
      }, disposable!!)

    override fun invoke() = SettingsSyncSettings.getInstance().syncEnabled
  }


  // indicates whether a provider has been selected initially
  inner class InitialProviderPredicate : ComponentPredicate() {
    override fun addListener(listener: (Boolean) -> Unit) =
      SettingsSyncEvents.getInstance().addListener(object : SettingsSyncEventListener {
        override fun loginStateChanged() {
          listener(invoke())
        }
      }, disposable!!)

    override fun invoke() = SettingsSyncLocalSettings.getInstance().userId == null
  }

  private data class UserProviderHolder(
    val userId: String,
    val userData: SettingsSyncUserData,
    val providerCode: String,
    val providerName: String,
  ) {
    override fun toString(): String {
      val printableName =  userData.printableName ?: userData.email ?: userData.name ?: userData.id
      return "$printableName ($providerName)"
    }
  }
}

class BackupNSyncConfigurableProvider : ConfigurableProvider() {
  override fun createConfigurable(): Configurable = BackupNSyncConfigurable()
}