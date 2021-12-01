package com.intellij.settingsSync

import com.intellij.configurationStore.ComponentStoreImpl
import com.intellij.ide.ApplicationLoadListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.components.stateStore
import com.intellij.util.SystemProperties
import com.intellij.util.messages.Topic
import java.nio.file.Path

@Topic.AppLevel
internal val SETTINGS_CHANGED_TOPIC: Topic<SettingsChangeListener> = Topic(SettingsChangeListener::class.java)

@Topic.AppLevel
internal val SETTINGS_LOGGED_TOPIC: Topic<SettingsLoggedListener> = Topic(SettingsLoggedListener::class.java)

private const val SETTINGS_SYNC_ENABLED_PROPERTY = "idea.settings.sync.enabled"

internal fun isSettingsSyncEnabled() : Boolean =
  SystemProperties.getBooleanProperty(SETTINGS_SYNC_ENABLED_PROPERTY, false)

class SettingsSyncFacade {
  internal val updateChecker: SettingsSyncUpdateChecker get() = getMain().controls.updateChecker

  internal fun pushSettingsToServer() {
    getMain().controls.pusher.push()
  }

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

      val settingsLog = GitSettingsLog(settingsSyncStorage, appConfigPath, parentDisposable, componentStore, AllShareableSettings())
      val pusher = SettingsSyncPusher(settingsLog, remoteCommunicator)
      val bridge = SettingsSyncBridge(application, parentDisposable, settingsLog, pusher)
      SettingsSyncIdeUpdater(application, componentStore, appConfigPath)

      val streamProvider = SettingsSyncStreamProvider(application, appConfigPath)
      componentStore.storageManager.addStreamProvider(streamProvider)

      val updateChecker = SettingsSyncUpdateChecker(application, remoteCommunicator)

      return SettingsSyncControls(updateChecker, pusher, bridge)
    }
  }

  internal class SettingsSyncControls(val updateChecker: SettingsSyncUpdateChecker,
                                      val pusher: SettingsSyncPusher,
                                      val bridge: SettingsSyncBridge)
}