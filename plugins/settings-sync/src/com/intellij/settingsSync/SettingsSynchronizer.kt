package com.intellij.settingsSync

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFrame
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class SettingsSynchronizer : ApplicationInitializedListener, ApplicationActivationListener, SettingsSyncEnabledStateListener {

  private val executorService = AppExecutorUtil.createBoundedScheduledExecutorService("Settings Sync Update", 1)
  private val autoSyncDelay get() = Registry.intValue("settingsSync.autoSync.frequency.sec", 60).toLong()

  private var scheduledFuture: ScheduledFuture<*>? = null // accessed only from the EDT

  override suspend fun execute(asyncScope: CoroutineScope) {
    if (!isSettingsSyncEnabledByKey()) {
      return
    }

    SettingsSyncEvents.getInstance().addEnabledStateChangeListener(this)

    if (isSettingsSyncEnabledInSettings()) {
      executorService.schedule(initializeSyncing(SettingsSyncBridge.InitMode.JustInit), 0, TimeUnit.SECONDS)
      return
    }

    if (!SettingsSyncSettings.getInstance().migrationFromOldStorageChecked) {
      val migration = MIGRATION_EP.extensionList.firstOrNull()
      if (migration != null) {
        val migrationPossible = migration.isLocalDataAvailable(PathManager.getConfigDir())
        LOG.info("Found migration from an old storage: ${migration.javaClass.name}, migration possible: $migrationPossible")
        SettingsSyncSettings.getInstance().migrationFromOldStorageChecked = true
        if (migrationPossible) {
          SettingsSyncSettings.getInstance().syncEnabled = true
          executorService.schedule(initializeSyncing(SettingsSyncBridge.InitMode.MigrateFromOldStorage(migration)), 0, TimeUnit.SECONDS)
        }
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
      scheduleSyncing("Syncing settings on app focus")
    }
  }

  override fun applicationDeactivated(ideFrame: IdeFrame) {
    stopSyncingByTimer()
  }

  private fun initializeSyncing(initMode: SettingsSyncBridge.InitMode): Runnable = Runnable {
    LOG.info("Initializing settings sync")
    val settingsSyncMain = SettingsSyncMain.getInstance()
    settingsSyncMain.controls.bridge.initialize(initMode)
    settingsSyncMain.syncSettings()
  }

  override fun enabledStateChanged(syncEnabled: Boolean) {
    // syncEnabled part is handled inside SettingsSyncEnabler
    if (!syncEnabled) {
      stopSyncingByTimer()
      SettingsSyncMain.getInstance().disableSyncing()
    }
  }

  private fun scheduleSyncing(logMessage: String) {
    executorService.schedule(Runnable {
      LOG.info(logMessage)
      SettingsSyncMain.getInstance().syncSettings()
    }, 0, TimeUnit.SECONDS)
  }

  @RequiresEdt
  private fun setupSyncingByTimer(): ScheduledFuture<*> {
    val delay = autoSyncDelay
    return executorService.scheduleWithFixedDelay(Runnable {
      LOG.info("Syncing settings by timer")
      SettingsSyncMain.getInstance().syncSettings()
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
  }
}