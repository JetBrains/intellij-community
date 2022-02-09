package com.intellij.settingsSync

import com.intellij.ide.FrameStateListener
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class SettingsSynchronizer : FrameStateListener, SettingsSyncEnabledStateListener {

  private val executorService = AppExecutorUtil.createBoundedScheduledExecutorService("Settings Sync Update", 1)
  private val autoSyncDelay get() = Registry.intValue("settingsSync.autoSync.frequency.sec", 60).toLong()

  private var scheduledFuture: ScheduledFuture<*>? = null // accessed only from the EDT

  override fun onFrameActivated() {
    if (!isSettingsSyncEnabledByKey() ||
        !isSettingsSyncEnabledInSettings()) {
      return
    }

    if (!SettingsSyncMain.isAvailable()) {
      executorService.schedule(initializeSyncing(), 0, TimeUnit.SECONDS)
      return
    }

    if (autoSyncDelay > 0 && scheduledFuture == null) {
      scheduledFuture = setupSyncingByTimer()
    }

    if (Registry.`is`("settingsSync.autoSync.on.focus", true)) {
      scheduleSyncing("Syncing settings on app focus")
    }
  }

  override fun onFrameDeactivated() {
    stopSyncingByTimer()
  }

  private fun initializeSyncing(): Runnable = Runnable {
    LOG.info("Initializing settings sync")
    SettingsSyncMain.getInstance().syncSettings()
  }

  override fun enabledStateChanged(syncEnabled: Boolean) {
    if (syncEnabled) {
      if (!SettingsSyncMain.isAvailable()) {
        executorService.schedule(initializeSyncing(), 0, TimeUnit.SECONDS)
      }
      else {
        SettingsSyncMain.getInstance().enableSyncing()
        scheduleSyncing("Syncing settings after enabling")
      }
    }
    else {
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
    val LOG = logger<SettingsSynchronizer>()
  }
}