package com.intellij.settingsSync

import com.intellij.ide.FrameStateListener
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class SettingsSyncFrameListener : FrameStateListener {
  private val executorService = AppExecutorUtil.createBoundedScheduledExecutorService("Settings Sync Update", 1)
  private val autoSyncDelay get() = Registry.intValue("settingsSync.autoSync.frequency.sec", 60).toLong()

  private var scheduledFuture: ScheduledFuture<*>? = null

  override fun onFrameActivated() {
    if (!isSettingsSyncEnabledByKey()) {
      return
    }

    if (autoSyncDelay > 0 && scheduledFuture == null) {
      scheduledFuture = syncSettingsByTimer()
    }

    if (Registry.`is`("settingsSync.autoSync.on.focus", true)) {
      executorService.schedule(Runnable {
        SettingsSyncMain.getInstance().syncSettings()
      }, 0, TimeUnit.SECONDS)
    }
  }

  override fun onFrameDeactivated() {
    if (scheduledFuture != null) {
      scheduledFuture!!.cancel(true)
      scheduledFuture = null
    }
  }

  private fun syncSettingsByTimer(): ScheduledFuture<*> {
    val delay = autoSyncDelay
    return executorService.scheduleWithFixedDelay(Runnable {
      SettingsSyncMain.getInstance().syncSettings()
    }, delay, delay, TimeUnit.SECONDS)
  }
}