package com.intellij.settingsSync

import com.intellij.configurationStore.ComponentStoreImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.components.stateStore
import com.intellij.settingsSync.auth.SettingsSyncAuthService
import com.intellij.util.SystemProperties
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

private const val SETTINGS_SYNC_ENABLED_PROPERTY = "idea.settings.sync.enabled"

@ApiStatus.Internal
fun isSettingsSyncEnabledByKey(): Boolean = SystemProperties.getBooleanProperty(SETTINGS_SYNC_ENABLED_PROPERTY, true)

internal fun isSettingsSyncEnabledInSettings(): Boolean = SettingsSyncSettings.getInstance().syncEnabled

internal const val SETTINGS_SYNC_STORAGE_FOLDER: String = "settingsSync"

@ApiStatus.Internal
@Service
class SettingsSyncMain : Disposable {
  val controls: SettingsSyncControls

  init {
    val appConfigPath = PathManager.getConfigDir()
    val componentStore = ApplicationManager.getApplication().stateStore as ComponentStoreImpl
    val ideMediator = SettingsSyncIdeMediatorImpl(componentStore = componentStore, rootConfig = appConfigPath, enabledCondition = {
      isSettingsSyncEnabledByKey() && isAvailable() && isSettingsSyncEnabledInSettings()
    })
    controls = init(parentDisposable = this,
                    settingsSyncStorage = appConfigPath.resolve(SETTINGS_SYNC_STORAGE_FOLDER),
                    appConfigPath = appConfigPath,
                    remoteCommunicator = CloudConfigServerCommunicator(),
                    ideMediator = ideMediator)
  }

  override fun dispose() {
  }

  internal fun getRemoteCommunicator(): SettingsSyncRemoteCommunicator = controls.remoteCommunicator

  fun disableSyncing() {
    controls.ideMediator.removeStreamProvider()
  }

  companion object {
    fun isAvailable(): Boolean {
      return ApplicationManager.getApplication().serviceIfCreated<SettingsSyncMain>() != null
    }

    fun getInstance(): SettingsSyncMain = service<SettingsSyncMain>()

    // Extracted to simplify testing, otherwise it is fast and is called from the service initializer
    internal fun init(parentDisposable: Disposable,
                      settingsSyncStorage: Path,
                      appConfigPath: Path,
                      remoteCommunicator: SettingsSyncRemoteCommunicator,
                      ideMediator: SettingsSyncIdeMediator): SettingsSyncControls {
      val settingsLog = GitSettingsLog(settingsSyncStorage, appConfigPath, parentDisposable,
                                       SettingsSyncAuthService.getInstance()::getUserData,
                                       initialSnapshotProvider = { currentSnapshot ->
                                         ideMediator.getInitialSnapshot(appConfigPath, currentSnapshot)
                                       })
      val updateChecker = SettingsSyncUpdateChecker(remoteCommunicator)
      val bridge = SettingsSyncBridge(parentDisposable, appConfigPath, settingsLog, ideMediator, remoteCommunicator, updateChecker)
      return SettingsSyncControls(ideMediator, updateChecker, bridge, remoteCommunicator, settingsSyncStorage)
    }
  }

  class SettingsSyncControls(val ideMediator: SettingsSyncIdeMediator,
                             val updateChecker: SettingsSyncUpdateChecker,
                             val bridge: SettingsSyncBridge,
                             val remoteCommunicator: SettingsSyncRemoteCommunicator,
                             val settingsSyncStorage: Path)
}
