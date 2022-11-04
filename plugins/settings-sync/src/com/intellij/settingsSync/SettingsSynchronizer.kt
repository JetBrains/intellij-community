package com.intellij.settingsSync

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFrame
import com.intellij.settingsSync.migration.SettingsRepositoryToSettingsSyncMigration
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class SettingsSynchronizer : ApplicationInitializedListener, ApplicationActivationListener, SettingsSyncEnabledStateListener, SettingsSyncCategoriesChangeListener {

  private val executorService = AppExecutorUtil.createBoundedScheduledExecutorService("Settings Sync Update", 1)
  private val autoSyncDelay get() = Registry.intValue("settingsSync.autoSync.frequency.sec", 60).toLong()

  private var scheduledFuture: ScheduledFuture<*>? = null // accessed only from the EDT

  override suspend fun execute(asyncScope: CoroutineScope) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment || !isSettingsSyncEnabledByKey()) {
      return
    }

    SettingsSyncEvents.getInstance().addEnabledStateChangeListener(this)

    if (isSettingsSyncEnabledInSettings()) {
      executorService.schedule(initializeSyncing(SettingsSyncBridge.InitMode.JustInit), 0, TimeUnit.SECONDS)
      return
    }

    if (!SettingsSyncSettings.getInstance().migrationFromOldStorageChecked) {
      SettingsSyncSettings.getInstance().migrationFromOldStorageChecked = true
      val migration = MIGRATION_EP.extensionList.firstOrNull { it.isLocalDataAvailable(PathManager.getConfigDir()) }
      if (migration != null) {
        LOG.info("Found migration from an old storage via ${migration.javaClass.simpleName}")
        executorService.schedule(initializeSyncing(SettingsSyncBridge.InitMode.MigrateFromOldStorage(migration)), 0, TimeUnit.SECONDS)
        SettingsSyncSettings.getInstance().syncEnabled = true
      }
      else {
        SettingsRepositoryToSettingsSyncMigration.migrateIfNeeded(executorService)
      }
    }
  }

  override fun applicationActivated(ideFrame: IdeFrame) {
    if (!isSettingsSyncEnabledByKey() || !isSettingsSyncEnabledInSettings() || !SettingsSyncMain.isAvailable()) {
      return
    }

    if (autoSyncDelay > 0 && scheduledFuture == null) {
      scheduledFuture = setupSyncingByTimer()
    }

    if (Registry.`is`("settingsSync.autoSync.on.focus", true)) {
      scheduleSyncingOnAppFocus()
    }
  }

  override fun applicationDeactivated(ideFrame: IdeFrame) {
    stopSyncingByTimer()
  }

  private fun initializeSyncing(initMode: SettingsSyncBridge.InitMode): Runnable = Runnable {
    LOG.info("Initializing settings sync")
    val settingsSyncMain = SettingsSyncMain.getInstance()
    settingsSyncMain.controls.bridge.initialize(initMode)
    syncSettings()
  }

  override fun enabledStateChanged(syncEnabled: Boolean) {
    if (syncEnabled) {
      SettingsSyncEvents.getInstance().addCategoriesChangeListener(this)
      // actual start of the sync is handled inside SettingsSyncEnabler
    }
    else {
      SettingsSyncEvents.getInstance().removeCategoriesChangeListener(this)
      stopSyncingByTimer()
      SettingsSyncMain.getInstance().disableSyncing()
    }
  }

  override fun categoriesStateChanged() {
    syncSettings()
  }

  private fun scheduleSyncingOnAppFocus() {
    executorService.schedule(Runnable {
      LOG.debug("Syncing settings on app focus")
      syncSettings()
    }, 0, TimeUnit.SECONDS)
  }

  @RequiresEdt
  private fun setupSyncingByTimer(): ScheduledFuture<*> {
    val delay = autoSyncDelay
    return executorService.scheduleWithFixedDelay(Runnable {
      LOG.debug("Syncing settings by timer")
      syncSettings()
    }, delay, delay, TimeUnit.SECONDS)
  }

  private fun syncSettings() {
    val syncControls = SettingsSyncMain.getInstance().controls
    syncSettings(syncControls.remoteCommunicator, syncControls.updateChecker)
  }

  @RequiresEdt
  private fun stopSyncingByTimer() {
    if (scheduledFuture != null) {
      scheduledFuture!!.cancel(true)
      scheduledFuture = null
    }
  }

  companion object {
    private val LOG = logger<SettingsSynchronizer>()

    private val MIGRATION_EP = ExtensionPointName.create<SettingsSyncMigration>("com.intellij.settingsSyncMigration")

    @RequiresBackgroundThread
    internal fun syncSettings(remoteCommunicator: SettingsSyncRemoteCommunicator, updateChecker: SettingsSyncUpdateChecker) {
      when (remoteCommunicator.checkServerState()) {
        is ServerState.UpdateNeeded -> {
          LOG.info("Updating from server")
          updateChecker.scheduleUpdateFromServer()
          // the push will happen automatically after updating and merging (if there is anything to merge)
        }
        ServerState.FileNotExists -> {
          LOG.info("No file on server")
        }
        ServerState.UpToDate -> {
          LOG.debug("Updating settings is not needed, will check if push is needed")
          SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.PingRequest)
        }
        is ServerState.Error -> {
          // error already logged in checkServerState, we schedule update
        }
      }
    }
  }
}