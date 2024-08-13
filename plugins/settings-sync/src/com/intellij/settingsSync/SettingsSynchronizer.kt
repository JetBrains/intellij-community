package com.intellij.settingsSync

import com.intellij.ide.ApplicationActivity
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFrame
import com.intellij.settingsSync.migration.migrateIfNeeded
import com.intellij.settingsSync.statistics.SettingsSyncEventsStatistics
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

private val LOG = logger<SettingsSynchronizer>()

private val MIGRATION_EP = ExtensionPointName<SettingsSyncMigration>("com.intellij.settingsSyncMigration")

private class SettingsSynchronizerApplicationInitializedListener : ApplicationActivity {
  init {
    if (ApplicationManager.getApplication().isHeadlessEnvironment || !isSettingsSyncEnabledByKey()) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute() {
    val settingsSyncEventListener = object : SettingsSyncEventListener {
      override fun categoriesStateChanged() {
        SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.LogCurrentSettings)
      }

      override fun enabledStateChanged(syncEnabled: Boolean) {
        if (syncEnabled) {
          SettingsSyncEvents.getInstance().addListener(this)
          // the actual start of the sync is handled inside SettingsSyncEnabler
        }
        else {
          SettingsSyncEvents.getInstance().removeListener(this)
          service<SettingsSynchronizerState>().stopSyncingByTimer()
          SettingsSyncMain.getInstance().disableSyncing()
        }
      }
    }
    serviceAsync<SettingsSyncEvents>().addListener(settingsSyncEventListener)

    if (isSettingsSyncEnabledInSettings()) {
      initializeSyncing(SettingsSyncBridge.InitMode.JustInit, settingsSyncEventListener)
      return
    }

    val syncSettings = serviceAsync<SettingsSyncSettings>()
    if (syncSettings.migrationFromOldStorageChecked) {
      return
    }

    syncSettings.migrationFromOldStorageChecked = true
    val migration = MIGRATION_EP.extensionList.firstOrNull { it.isLocalDataAvailable(PathManager.getConfigDir()) }
    if (migration != null) {
      LOG.info("Found migration from an old storage via ${migration.javaClass.simpleName}")
      initializeSyncing(SettingsSyncBridge.InitMode.MigrateFromOldStorage(migration), settingsSyncEventListener)
      SettingsSyncEventsStatistics.MIGRATED_FROM_OLD_PLUGIN.log()
    }
    else {
      coroutineScope {
        migrateIfNeeded(this, serviceAsync<SettingsSynchronizerState>().executorService)
      }
    }
  }
}

private suspend fun initializeSyncing(initMode: SettingsSyncBridge.InitMode, settingsSyncEventListener: SettingsSyncEventListener) {
  LOG.info("Initializing settings sync. Mode: $initMode")
  val settingsSyncMain = serviceAsync<SettingsSyncMain>()
  blockingContext {
    settingsSyncMain.controls.bridge.initialize(initMode)
    val settingsSyncEvents = SettingsSyncEvents.getInstance()
    settingsSyncEvents.addListener(settingsSyncEventListener)
    settingsSyncEvents.fireSettingsChanged(SyncSettingsEvent.SyncRequest)
    LocalHostNameProvider.initialize()
  }
}

@Service
private class SettingsSynchronizerState {
  @JvmField val executorService = AppExecutorUtil.createBoundedScheduledExecutorService("Settings Sync Update", 1)

  // accessed only from the EDT
  @JvmField var scheduledFuture: ScheduledFuture<*>? = null

  @RequiresEdt
  fun stopSyncingByTimer() {
    if (scheduledFuture != null) {
      scheduledFuture!!.cancel(true)
      scheduledFuture = null
    }
  }
}

private class SettingsSynchronizer : ApplicationActivationListener {
  private val autoSyncDelay: Long
    get() = Registry.intValue("settingsSync.autoSync.frequency.sec", 60).toLong()

  override fun applicationActivated(ideFrame: IdeFrame) {
    if (!isSettingsSyncEnabledByKey() || !isSettingsSyncEnabledInSettings() || !SettingsSyncMain.isAvailable()) {
      return
    }

    if (autoSyncDelay > 0 && service<SettingsSynchronizerState>().scheduledFuture == null) {
      service<SettingsSynchronizerState>().scheduledFuture = setupSyncingByTimer()
    }

    if (Registry.`is`("settingsSync.autoSync.on.focus", true)) {
      SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.SyncRequest)
    }
  }

  override fun applicationDeactivated(ideFrame: IdeFrame) {
    service<SettingsSynchronizerState>().stopSyncingByTimer()
  }

  @RequiresEdt
  private fun setupSyncingByTimer(): ScheduledFuture<*> {
    val delay = autoSyncDelay
    return service<SettingsSynchronizerState>().executorService.scheduleWithFixedDelay(Runnable {
      LOG.debug("Syncing settings by timer")
      SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.SyncRequest)
    }, delay, delay, TimeUnit.SECONDS)
  }
}

internal fun enabledOrDisabled(value: Boolean?): String {
  return if (value == null) "null" else if (value) "enabled" else "disabled"
}