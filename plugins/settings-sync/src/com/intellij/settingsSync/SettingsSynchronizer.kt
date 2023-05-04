package com.intellij.settingsSync

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFrame
import com.intellij.settingsSync.migration.SettingsRepositoryToSettingsSyncMigration
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class SettingsSynchronizer : ApplicationInitializedListener, ApplicationActivationListener, SettingsSyncEnabledStateListener, SettingsSyncCategoriesChangeListener {

  private val executorService = AppExecutorUtil.createBoundedScheduledExecutorService("Settings Sync Update", 1)
  private val autoSyncDelay get() = Registry.intValue("settingsSync.autoSync.frequency.sec", 60).toLong()

  private var scheduledFuture: ScheduledFuture<*>? = null // accessed only from the EDT

  override suspend fun execute(asyncScope: CoroutineScope) : Unit = blockingContext {
    if (ApplicationManager.getApplication().isHeadlessEnvironment || !isSettingsSyncEnabledByKey()) {
      return@blockingContext
    }

    SettingsSyncEvents.getInstance().addEnabledStateChangeListener(this)

    if (isSettingsSyncEnabledInSettings()) {
      executorService.schedule(initializeSyncing(SettingsSyncBridge.InitMode.JustInit), 0, TimeUnit.SECONDS)
      return@blockingContext
    }

    if (!SettingsSyncSettings.getInstance().migrationFromOldStorageChecked) {
      SettingsSyncSettings.getInstance().migrationFromOldStorageChecked = true
      val migration = MIGRATION_EP.extensionList.firstOrNull { it.isLocalDataAvailable(PathManager.getConfigDir()) }
      if (migration != null) {
        LOG.info("Found migration from an old storage via ${migration.javaClass.simpleName}")
        executorService.schedule(initializeSyncing(SettingsSyncBridge.InitMode.MigrateFromOldStorage(migration)), 0, TimeUnit.SECONDS)
        SettingsSyncEventsStatistics.MIGRATED_FROM_OLD_PLUGIN.log()
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
      syncSettings()
    }
  }

  override fun applicationDeactivated(ideFrame: IdeFrame) {
    stopSyncingByTimer()
  }

  private fun initializeSyncing(initMode: SettingsSyncBridge.InitMode): Runnable = Runnable {
    LOG.info("Initializing settings sync")
    val settingsSyncMain = SettingsSyncMain.getInstance()
    settingsSyncMain.controls.bridge.initialize(initMode)
    SettingsSyncEvents.getInstance().addCategoriesChangeListener(this)
    syncSettings()
    LocalHostNameProvider.initialize()
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
    SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.LogCurrentSettings)
  }

  @RequiresEdt
  private fun setupSyncingByTimer(): ScheduledFuture<*> {
    val delay = autoSyncDelay
    return executorService.scheduleWithFixedDelay(Runnable {
      LOG.debug("Syncing settings by timer")
      syncSettings()
    }, delay, delay, TimeUnit.SECONDS)
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

    internal fun syncSettings() {
      SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.SyncRequest)
    }

    internal fun checkCrossIdeSyncStatusOnServer(remoteCommunicator: SettingsSyncRemoteCommunicator) {
      try {
        val crossIdeSyncEnabled = remoteCommunicator.isFileExists(CROSS_IDE_SYNC_MARKER_FILE)
        if (crossIdeSyncEnabled != SettingsSyncLocalSettings.getInstance().isCrossIdeSyncEnabled) {
          LOG.info("Cross-IDE sync status on server is: ${enabledOrDisabled(crossIdeSyncEnabled)}. Updating local settings with it.")
          SettingsSyncLocalSettings.getInstance().isCrossIdeSyncEnabled = crossIdeSyncEnabled
        }
      }
      catch (e: Throwable) {
        LOG.error("Couldn't check if $CROSS_IDE_SYNC_MARKER_FILE exists", e)
      }
    }
  }
}

internal const val CROSS_IDE_SYNC_MARKER_FILE = "cross-ide-sync-enabled"

internal fun enabledOrDisabled(value: Boolean?) = if (value == null) "null" else if (value) "enabled" else "disabled"