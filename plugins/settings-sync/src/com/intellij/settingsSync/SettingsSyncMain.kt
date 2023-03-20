package com.intellij.settingsSync

import com.intellij.configurationStore.ComponentStoreImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.SystemProperties
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

private const val SETTINGS_SYNC_ENABLED_PROPERTY = "idea.settings.sync.enabled"

@ApiStatus.Internal
fun isSettingsSyncEnabledByKey(): Boolean =
  SystemProperties.getBooleanProperty(SETTINGS_SYNC_ENABLED_PROPERTY, true)

internal fun isSettingsSyncEnabledInSettings(): Boolean =
  SettingsSyncSettings.getInstance().syncEnabled

internal const val SETTINGS_SYNC_STORAGE_FOLDER = "settingsSync"

@ApiStatus.Internal
class SettingsSyncMain : Disposable {

  val controls: SettingsSyncControls
  private val componentStore: ComponentStoreImpl

  init {
    val application = ApplicationManager.getApplication()
    val appConfigPath = PathManager.getConfigDir()
    val settingsSyncStorage = appConfigPath.resolve(SETTINGS_SYNC_STORAGE_FOLDER)
    val remoteCommunicator = CloudConfigServerCommunicator()

    componentStore = application.stateStore as ComponentStoreImpl
    val ideMediator = SettingsSyncIdeMediatorImpl(componentStore, appConfigPath, enabledCondition = {
      isSettingsSyncEnabledByKey() && isAvailable() && isSettingsSyncEnabledInSettings()
    })
    controls = init(application, this, settingsSyncStorage, appConfigPath, remoteCommunicator, ideMediator)
  }

  override fun dispose() {
  }

  internal fun getRemoteCommunicator(): SettingsSyncRemoteCommunicator = controls.remoteCommunicator

  fun disableSyncing() {
    controls.ideMediator.removeStreamProvider()
  }

  companion object {

    fun isAvailable(): Boolean {
      return ApplicationManager.getApplication().getServiceIfCreated(SettingsSyncMain::class.java) != null
    }

    fun getInstance(): SettingsSyncMain = ApplicationManager.getApplication().getService(SettingsSyncMain::class.java)

    // Extracted to simplify testing, otherwise it is fast and is called from the service initializer
    internal fun init(application: Application,
                      parentDisposable: Disposable,
                      settingsSyncStorage: Path,
                      appConfigPath: Path,
                      remoteCommunicator: SettingsSyncRemoteCommunicator,
                      ideMediator: SettingsSyncIdeMediator): SettingsSyncControls {
      val settingsLog = GitSettingsLog(settingsSyncStorage, appConfigPath, parentDisposable,
        initialSnapshotProvider = { currentSnapshot -> ideMediator.getInitialSnapshot(appConfigPath, currentSnapshot) })
      val updateChecker = SettingsSyncUpdateChecker(remoteCommunicator)
      val bridge = SettingsSyncBridge(parentDisposable, appConfigPath, settingsLog, ideMediator, remoteCommunicator, updateChecker)
      return SettingsSyncControls(ideMediator, updateChecker, bridge, remoteCommunicator, settingsSyncStorage)
    }

    private val LOG = logger<SettingsSyncMain>()
  }

  class SettingsSyncControls(val ideMediator: SettingsSyncIdeMediator,
                             val updateChecker: SettingsSyncUpdateChecker,
                             val bridge: SettingsSyncBridge,
                             val remoteCommunicator: SettingsSyncRemoteCommunicator,
                             val settingsSyncStorage: Path)
}
