package com.intellij.settingsSync

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFrame
import com.intellij.settingsSync.migration.migrateIfNeeded
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

private val LOG = logger<SettingsSynchronizer>()

private val MIGRATION_EP = ExtensionPointName<SettingsSyncMigration>("com.intellij.settingsSyncMigration")

internal class SettingsSynchronizer : ApplicationInitializedListener, ApplicationActivationListener, SettingsSyncEventListener {
  private val executorService = AppExecutorUtil.createBoundedScheduledExecutorService("Settings Sync Update", 1)
  private val autoSyncDelay: Long
    get() = Registry.intValue("settingsSync.autoSync.frequency.sec", 60).toLong()

  private var scheduledFuture: ScheduledFuture<*>? = null // accessed only from the EDT

  override suspend fun execute(asyncScope: CoroutineScope) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment || !isSettingsSyncEnabledByKey()) {
      return
    }

    asyncScope.launch {
      SettingsSyncEvents.getInstance().addListener(this@SettingsSynchronizer)

      if (isSettingsSyncEnabledInSettings()) {
        initializeSyncing(SettingsSyncBridge.InitMode.JustInit)
        return@launch
      }

      if (!SettingsSyncSettings.getInstance().migrationFromOldStorageChecked) {
        SettingsSyncSettings.getInstance().migrationFromOldStorageChecked = true
        val migration = MIGRATION_EP.extensionList.firstOrNull { it.isLocalDataAvailable(PathManager.getConfigDir()) }
        if (migration != null) {
          LOG.info("Found migration from an old storage via ${migration.javaClass.simpleName}")
          initializeSyncing(SettingsSyncBridge.InitMode.MigrateFromOldStorage(migration))
          SettingsSyncEventsStatistics.MIGRATED_FROM_OLD_PLUGIN.log()
        }
        else {
          migrateIfNeeded(executorService)
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
      fireSettingsChanged()
    }
  }

  override fun applicationDeactivated(ideFrame: IdeFrame) {
    stopSyncingByTimer()
  }

  private suspend fun initializeSyncing(initMode: SettingsSyncBridge.InitMode) {
    LOG.info("Initializing settings sync")
    val settingsSyncMain = serviceAsync<SettingsSyncMain>()
    blockingContext {
      settingsSyncMain.controls.bridge.initialize(initMode)
      val settingsSyncEvents = SettingsSyncEvents.getInstance()
      settingsSyncEvents.addListener(this)
      settingsSyncEvents.fireSettingsChanged(SyncSettingsEvent.SyncRequest)
      LocalHostNameProvider.initialize()
    }
  }

  override fun enabledStateChanged(syncEnabled: Boolean) {
    if (syncEnabled) {
      SettingsSyncEvents.getInstance().addListener(this)
      // the actual start of the sync is handled inside SettingsSyncEnabler
    }
    else {
      SettingsSyncEvents.getInstance().removeListener(this)
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
      fireSettingsChanged()
    }, delay, delay, TimeUnit.SECONDS)
  }

  @RequiresEdt
  private fun stopSyncingByTimer() {
    if (scheduledFuture != null) {
      scheduledFuture!!.cancel(true)
      scheduledFuture = null
    }
  }
}

internal fun fireSettingsChanged() {
  SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.SyncRequest)
}

internal fun enabledOrDisabled(value: Boolean?): String {
  return if (value == null) "null" else if (value) "enabled" else "disabled"
}