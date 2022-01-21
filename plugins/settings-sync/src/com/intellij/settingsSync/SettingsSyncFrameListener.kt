package com.intellij.settingsSync

import com.intellij.ide.FrameStateListener
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class SettingsSyncFrameListener : FrameStateListener {

  private val executorService = AppExecutorUtil.createBoundedScheduledExecutorService("Settings Sync Update", 1)
  private val autoSyncDelay get() = Registry.intValue("settingsSync.autoSync.frequency.sec", 60).toLong()

  private var scheduledFuture: ScheduledFuture<*>? = null // accessed only from the EDT

  override fun onFrameActivated() {
    if (!isSettingsSyncEnabledByKey() ||
        !isSettingsSyncEnabledInSettings()) {
      return
    }

    if (!SettingsSyncMain.isAvailable()) {
      LOG.info("Initializing settings sync")
      executorService.schedule(initializeSyncing(), 0, TimeUnit.SECONDS)
      return
    }

    if (autoSyncDelay > 0 && scheduledFuture == null) {
      scheduledFuture = setupSyncingByTimer()
    }

    if (Registry.`is`("settingsSync.autoSync.on.focus", true)) {
      scheduleSyncing()
    }
  }

  override fun onFrameDeactivated() {
    stopSyncingByTimer()
  }

  private fun initializeSyncing() : Runnable = Runnable {
    SettingsSyncMain.getInstance().syncSettings()

    SettingsSyncSettings.getInstance().addListener(SettingsSyncListener(), SettingsSyncMain.getInstance())
  }

  private inner class SettingsSyncListener : SettingsSyncSettings.Listener {
    @RequiresEdt
    override fun settingsChanged() {
      if (SettingsSyncSettings.getInstance().syncEnabled) {
        scheduleSyncing()
      }
      else {
        stopSyncingByTimer()
      }
    }
  }

  private fun scheduleSyncing() {
    executorService.schedule(Runnable {
      SettingsSyncMain.getInstance().syncSettings()
    }, 0, TimeUnit.SECONDS)
  }

  @RequiresEdt
  private fun setupSyncingByTimer(): ScheduledFuture<*> {
    val delay = autoSyncDelay
    return executorService.scheduleWithFixedDelay(Runnable {
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
    val LOG = logger<SettingsSyncFrameListener>()
  }
}