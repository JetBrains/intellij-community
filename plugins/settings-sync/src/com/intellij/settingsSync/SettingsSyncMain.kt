package com.intellij.settingsSync

import com.intellij.configurationStore.ComponentStoreImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.settingsSync.communicator.RemoteCommunicatorHolder
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
fun isSettingsSyncEnabledInSettings(): Boolean = SettingsSyncSettings.getInstance().syncEnabled

internal const val SETTINGS_SYNC_STORAGE_FOLDER: String = "settingsSync"

@ApiStatus.Internal
@Service
class SettingsSyncMain(coroutineScope: CoroutineScope) : Disposable {
  val controls: SettingsSyncControls

  init {
    val appConfigPath = PathManager.getConfigDir()
    val componentStore = ApplicationManager.getApplication().stateStore as ComponentStoreImpl
    val ideMediator = SettingsSyncIdeMediatorImpl(componentStore = componentStore, rootConfig = appConfigPath, enabledCondition = {
      isAvailable() && isSettingsSyncEnabledInSettings()
    })
    controls = init(coroutineScope,
                    parentDisposable = this,
                    settingsSyncStorage = appConfigPath.resolve(SETTINGS_SYNC_STORAGE_FOLDER),
                    appConfigPath = appConfigPath,
                    ideMediator = ideMediator)
  }

  override fun dispose() {
  }

  fun disableSyncing() {
    controls.ideMediator.removeStreamProvider()
  }

  companion object {
    fun isAvailable(): Boolean {
      return ApplicationManager.getApplication().serviceIfCreated<SettingsSyncMain>() != null
    }

    fun getInstance(): SettingsSyncMain = service<SettingsSyncMain>()

    // Extracted to simplify testing, otherwise it is fast and is called from the service initializer
    internal fun init(
      coroutineScope: CoroutineScope,
      parentDisposable: Disposable,
      settingsSyncStorage: Path,
      appConfigPath: Path,
      ideMediator: SettingsSyncIdeMediator,
    ): SettingsSyncControls {
      val settingsLog = GitSettingsLog(settingsSyncStorage, appConfigPath, parentDisposable,
                                       RemoteCommunicatorHolder.getAuthService()::getUserData,
                                       initialSnapshotProvider = { currentSnapshot ->
                                         ideMediator.getInitialSnapshot(appConfigPath, currentSnapshot)
                                       })
      val updateChecker = SettingsSyncUpdateChecker()
      val bridge = SettingsSyncBridge(coroutineScope, appConfigPath, settingsLog, ideMediator, updateChecker)
      return SettingsSyncControls(ideMediator, updateChecker, bridge, settingsSyncStorage)
    }
  }

  class SettingsSyncControls(val ideMediator: SettingsSyncIdeMediator,
                             val updateChecker: SettingsSyncUpdateChecker,
                             val bridge: SettingsSyncBridge,
                             val settingsSyncStorage: Path)
}
