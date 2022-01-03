package com.intellij.settingsSync

import com.intellij.configurationStore.ComponentStoreImpl
import com.intellij.configurationStore.getExportableComponentsMap
import com.intellij.configurationStore.getExportableItemsFromLocalStorage
import com.intellij.ide.ApplicationLoadListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.stateStore
import com.intellij.util.SystemProperties
import com.intellij.util.messages.Topic
import java.nio.file.Path

@Topic.AppLevel
internal val SETTINGS_CHANGED_TOPIC: Topic<SettingsChangeListener> = Topic(SettingsChangeListener::class.java)

private const val SETTINGS_SYNC_ENABLED_PROPERTY = "idea.settings.sync.enabled"

internal fun isSettingsSyncEnabled() : Boolean =
  SystemProperties.getBooleanProperty(SETTINGS_SYNC_ENABLED_PROPERTY, false)

internal class SettingsSyncFacade {
  internal val updateChecker: SettingsSyncUpdateChecker get() = getMain().controls.updateChecker

  internal fun pushSettingsToServer() {
    ApplicationManager.getApplication().messageBus.syncPublisher(SETTINGS_CHANGED_TOPIC).settingChanged(SyncSettingsEvent.PushRequest())
  }

  internal fun getRemoteCommunicator(): SettingsSyncRemoteCommunicator = getMain().controls.remoteCommunicator

  private fun getMain(): SettingsSyncMain {
    return ApplicationLoadListener.EP_NAME.findExtensionOrFail(SettingsSyncMain::class.java)
  }
}

internal class SettingsSyncMain : ApplicationLoadListener {

  internal lateinit var controls: SettingsSyncControls

  override fun beforeApplicationLoaded(application: Application, appConfigPath: Path) {
    if (application.isUnitTestMode || !isSettingsSyncEnabled()) {
      return
    }

    val settingsSyncStorage = appConfigPath.resolve("settingsSync")
    val remoteCommunicator = if (System.getProperty(SETTINGS_SYNC_LOCAL_SERVER_PATH_PROPERTY) != null)
      LocalDirSettingsSyncRemoteCommunicator(settingsSyncStorage)
    else CloudConfigServerCommunicator()

    @Suppress("IncorrectParentDisposable") // settings sync is enabled on startup => Application is the only possible disposable parent
    controls = init(application, application, settingsSyncStorage, appConfigPath, application.stateStore as ComponentStoreImpl, remoteCommunicator)
  }

  companion object {

    internal fun init(application: Application,
                      parentDisposable: Disposable,
                      settingsSyncStorage: Path,
                      appConfigPath: Path,
                      componentStore: ComponentStoreImpl,
                      remoteCommunicator: SettingsSyncRemoteCommunicator): SettingsSyncControls {
      // todo migrate from cloud config or settings-repository
      // todo set provider only if connected

      val settingsLog = GitSettingsLog(settingsSyncStorage, appConfigPath, parentDisposable) {
        getExportableItemsFromLocalStorage(getExportableComponentsMap(false), componentStore.storageManager).keys
      }
      val ideUpdater = SettingsSyncIdeUpdater(application, componentStore, appConfigPath)
      val updateChecker = SettingsSyncUpdateChecker(application, remoteCommunicator)
      val bridge = SettingsSyncBridge(application, parentDisposable, settingsLog, ideUpdater, remoteCommunicator, updateChecker)

      val streamProvider = SettingsSyncStreamProvider(application, appConfigPath)
      componentStore.storageManager.addStreamProvider(streamProvider)


      return SettingsSyncControls(updateChecker, bridge, remoteCommunicator)
    }
  }

  internal class SettingsSyncControls(val updateChecker: SettingsSyncUpdateChecker,
                                      val bridge: SettingsSyncBridge,
                                      val remoteCommunicator: SettingsSyncRemoteCommunicator)
}