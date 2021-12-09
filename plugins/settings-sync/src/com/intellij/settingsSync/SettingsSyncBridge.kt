package com.intellij.settingsSync

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.util.Alarm
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.TimeUnit

/**
 * Handles events about settings change both from the current IDE, and from the server, merges the settings, logs them,
 * and provides the combined data to clients: both to the IDE and to the server.
 */
internal class SettingsSyncBridge(private val application: Application,
                                  parentDisposable: Disposable,
                                  private val settingsLog: SettingsLog,
                                  private val pusher: SettingsSyncPusher) {

  private val pendingEvents = ContainerUtil.createConcurrentList<SettingsChangeEvent>()

  private val queue = MergingUpdateQueue("SettingsSyncBridge", 1000, true, null, parentDisposable, null, Alarm.ThreadToUse.POOLED_THREAD).
    apply {
      setRestartTimerOnAdd(true)
    }

  private val updateObject = object : Update(1) { // all requests are always merged
    override fun run() {
      processPendingEvents()
      // todo what if here a new event is added; probably synchronization is needed between pPE and adding to the queue
    }
  }

  init {
    application.messageBus.connect(parentDisposable).subscribe(SETTINGS_CHANGED_TOPIC, object : SettingsChangeListener {
      override fun settingChanged(event: SettingsChangeEvent) {
        pendingEvents.add(event)
        queue.queue(updateObject)
      }
    })
  }

  @RequiresBackgroundThread
  private fun processPendingEvents() {
    while (pendingEvents.isNotEmpty()) {
      val event = pendingEvents.removeAt(0)
      processSettingsChangeEvent(event)
    }
    val snap = settingsLog.getCurrentSnapshot()
    // todo only after changes from server or merges
    application.messageBus.syncPublisher(SETTINGS_LOGGED_TOPIC).settingsLogged(SettingsLoggedEvent(snap, true, true, emptySet()))
  }

  @RequiresBackgroundThread
  private fun processSettingsChangeEvent(event: SettingsChangeEvent) {
    if (event.snapshot.isEmpty()) {
      return
    }

    if (event.source == ChangeSource.FROM_LOCAL) {
      settingsLog.recordLocalState(event.snapshot)
      push() // todo push only after processing all events, to avoid extra pushes
    }
    else if (event.source == ChangeSource.FROM_SERVER) {
      val merged = settingsLog.pull(event.snapshot)
      if (merged) {
        push()
      }
    }
  }

  private fun push() {
    pusher.push()
  }

  @TestOnly
  fun waitForAllExecuted(timeout: Long, timeUnit: TimeUnit) {
    queue.waitForAllExecuted(timeout, timeUnit)
  }

  @VisibleForTesting
  internal fun suspendEventProcessing() {
    queue.suspend()
  }

  @VisibleForTesting
  internal fun resumeEventProcessing() {
    queue.resume()
  }
}