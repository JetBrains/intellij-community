package com.intellij.settingsSync

import com.intellij.configurationStore.ComponentStoreImpl
import com.intellij.configurationStore.getExportableComponentsMap
import com.intellij.configurationStore.getExportableItemsFromLocalStorage
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.messages.Topic
import java.nio.file.Path

@Topic.AppLevel
internal val SETTINGS_CHANGED_TOPIC: Topic<SettingsChangeListener> = Topic(SettingsChangeListener::class.java)

private const val SETTINGS_SYNC_ENABLED_PROPERTY = "idea.settings.sync.enabled"

internal fun isSettingsSyncEnabledByKey() : Boolean =
  SystemProperties.getBooleanProperty(SETTINGS_SYNC_ENABLED_PROPERTY, false)

internal fun isSettingsSyncEnabledInSettings() : Boolean =
  SettingsSyncSettings.getInstance().syncEnabled

internal class SettingsSyncMain : Disposable {

  internal val controls: SettingsSyncControls
  private val componentStore: ComponentStoreImpl

  init {
    val application = ApplicationManager.getApplication()
    val appConfigPath = PathManager.getConfigDir()
    val settingsSyncStorage = appConfigPath.resolve("settingsSync")
    val remoteCommunicator = if (System.getProperty(SETTINGS_SYNC_LOCAL_SERVER_PATH_PROPERTY) != null)
      LocalDirSettingsSyncRemoteCommunicator(settingsSyncStorage)
    else CloudConfigServerCommunicator()

    componentStore = application.stateStore as ComponentStoreImpl
    controls = init(application, this, settingsSyncStorage, appConfigPath, componentStore, remoteCommunicator)
  }

  override fun dispose() {
  }

  internal fun getRemoteCommunicator(): SettingsSyncRemoteCommunicator = controls.remoteCommunicator

  @RequiresBackgroundThread
  internal fun syncSettings() {
    val publisher = ApplicationManager.getApplication().messageBus.syncPublisher(SETTINGS_CHANGED_TOPIC)
    when (controls.remoteCommunicator.checkServerState()) {
      is ServerState.UpdateNeeded -> {
        LOG.info("Updating from server")
        controls.updateChecker.scheduleUpdateFromServer()
        // the push will happen automatically after updating and merging (if there is anything to merge)
      }
      ServerState.FileNotExists -> {
        LOG.info("No file on server, we must push")
        publisher.settingChanged(SyncSettingsEvent.MustPushRequest)
      }
      ServerState.UpToDate -> {
        LOG.info("Updating settings is not needed, will check if push is needed")
        publisher.settingChanged(SyncSettingsEvent.PushIfNeededRequest)
      }
      is ServerState.Error -> {
        // error already logged in checkServerState, we schedule update
      }
    }
  }

  fun enableSyncing() {
    componentStore.storageManager.addStreamProvider(controls.streamProvider, true)
  }

  fun disableSyncing() {
    componentStore.storageManager.removeStreamProvider(controls.streamProvider::class.java)
  }

  internal companion object {

    fun isAvailable(): Boolean {
      return ApplicationManager.getApplication().getServiceIfCreated(SettingsSyncMain::class.java) != null
    }

    fun getInstance(): SettingsSyncMain = ApplicationManager.getApplication().getService(SettingsSyncMain::class.java)

    // Extracted to simplify testing, otherwise it is fast and is called from the service initializer
    internal fun init(application: Application,
                      parentDisposable: Disposable,
                      settingsSyncStorage: Path,
                      appConfigPath: Path,
                      componentStore: ComponentStoreImpl,
                      remoteCommunicator: SettingsSyncRemoteCommunicator): SettingsSyncControls {
      // todo migrate from cloud config or settings-repository

      val settingsLog = GitSettingsLog(settingsSyncStorage, appConfigPath, parentDisposable) {
        getExportableItemsFromLocalStorage(getExportableComponentsMap(false), componentStore.storageManager).keys
      }
      val ideUpdater = SettingsSyncIdeUpdater(componentStore, appConfigPath)
      val updateChecker = SettingsSyncUpdateChecker(application, remoteCommunicator)
      val bridge = SettingsSyncBridge(application, parentDisposable, settingsLog, ideUpdater, remoteCommunicator, updateChecker)

      val streamProvider = SettingsSyncStreamProvider(application, appConfigPath)
      componentStore.storageManager.addStreamProvider(streamProvider, true)

      return SettingsSyncControls(streamProvider, updateChecker, bridge, remoteCommunicator)
    }

    private val LOG = logger<SettingsSyncMain>()
  }

  internal class SettingsSyncControls(val streamProvider: SettingsSyncStreamProvider,
                                      val updateChecker: SettingsSyncUpdateChecker,
                                      val bridge: SettingsSyncBridge,
                                      val remoteCommunicator: SettingsSyncRemoteCommunicator)
}
