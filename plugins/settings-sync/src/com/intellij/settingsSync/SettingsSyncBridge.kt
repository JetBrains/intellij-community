package com.intellij.settingsSync

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
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
internal class SettingsSyncBridge(parentDisposable: Disposable,
                                  private val settingsLog: SettingsLog,
                                  private val ideUpdater: SettingsSyncIdeCommunicator,
                                  private val remoteCommunicator: SettingsSyncRemoteCommunicator,
                                  private val updateChecker: SettingsSyncUpdateChecker,
                                  private val activateStreamProvider: () -> Unit
) {

  private val pendingEvents = ContainerUtil.createConcurrentList<SyncSettingsEvent>()

  private val queue = MergingUpdateQueue("SettingsSyncBridge", 1000, true, null, parentDisposable, null,
                                         Alarm.ThreadToUse.POOLED_THREAD).apply {
    setRestartTimerOnAdd(true)
  }

  private val updateObject = object : Update(1) { // all requests are always merged
    override fun run() {
      processPendingEvents()
      // todo what if here a new event is added; probably synchronization is needed between pPE and adding to the queue
    }
  }

  @RequiresBackgroundThread
  fun initialize(initMode: InitMode) {
    settingsLog.initialize()
    // activate the stream provider at this point to correctly process the event from the server (e.g. to synchronize the access to xmls)
    activateStreamProvider()

    when (initMode) {
      is InitMode.TakeFromServer -> {
        pendingEvents.add(initMode.cloudEvent)
        processPendingEvents()
      }
      is InitMode.PushToServer -> {
        pendingEvents.add(SyncSettingsEvent.MustPushRequest)
        processPendingEvents()
      }
      InitMode.JustInit -> {} // nothing to do
    }

    // todo copy existing settings again here
    // because local events could happen between activating the stream provider above and starting processing events here
    SettingsSyncEvents.getInstance().addSettingsChangedListener { event ->
      pendingEvents.add(event)
      queue.queue(updateObject)
    }
  }

  sealed class InitMode {
    object JustInit : InitMode()
    class TakeFromServer(val cloudEvent: SyncSettingsEvent.CloudChange) : InitMode()
    object PushToServer : InitMode()
  }

  @RequiresBackgroundThread
  private fun processPendingEvents() {
    val previousIdePosition = settingsLog.getIdePosition()
    val previousCloudPosition = settingsLog.getCloudPosition()

    var pushToCloudRequested = false
    var mustPushToCloud = false
    while (pendingEvents.isNotEmpty()) {
      val event = pendingEvents.removeAt(0)
      if (event is SyncSettingsEvent.IdeChange) {
        settingsLog.applyIdeState(event.snapshot)
      }
      else if (event is SyncSettingsEvent.CloudChange) {
        settingsLog.applyCloudState(event.snapshot)
      }
      else if (event is SyncSettingsEvent.PushIfNeededRequest) {
        pushToCloudRequested = true
      }
      else if (event is SyncSettingsEvent.MustPushRequest) {
        mustPushToCloud = true
      }
    }

    val newIdePosition = settingsLog.getIdePosition()
    val newCloudPosition = settingsLog.getCloudPosition()
    val masterPosition: SettingsLog.Position
    if (newIdePosition != previousIdePosition || newCloudPosition != previousCloudPosition) {
      // move master to the actual position. It can be a fast-forward to either ide, or cloud changes, or it can be a merge
      masterPosition = settingsLog.advanceMaster()
    }
    else {
      // there were only fake events without actual changes to the repository => master doesn't need to be changed either
      masterPosition = settingsLog.getMasterPosition()
    }

    if (newIdePosition != masterPosition) { // master has advanced further that ide => the ide needs to be updated
      val pushResult: SettingsSyncPushResult = pushToIde(settingsLog.collectCurrentSnapshot())
      LOG.info("Result of pushing settings to the IDE: $pushResult")
      when (pushResult) {
        SettingsSyncPushResult.Success -> settingsLog.setIdePosition(masterPosition)
        is SettingsSyncPushResult.Error -> {
          // todo notify only in case of explicit sync invocation, otherwise update some SettingsSyncStatus
          notifySettingsSyncError(title = SettingsSyncBundle.message("notification.title.apply.error"),
                                  message = pushResult.message)
        }
        SettingsSyncPushResult.Rejected -> {
          // In the case of reject we'll just "wait" for the next update event:
          // it will be processed in the next session anyway
          if (pendingEvents.none { it is SyncSettingsEvent.IdeChange }) {
            // todo schedule update
          }
        }
      }
    }

    if (newCloudPosition != masterPosition || mustPushToCloud) {
      val pushResult: SettingsSyncPushResult = pushToCloud(settingsLog.collectCurrentSnapshot())
      LOG.info("Result of pushing settings to the cloud: $pushResult")
      when (pushResult) {
        SettingsSyncPushResult.Success -> settingsLog.setCloudPosition(masterPosition)
        is SettingsSyncPushResult.Error -> {
          // todo notify only in case of explicit sync invocation, otherwise update some SettingsSyncStatus
          notifySettingsSyncError(SettingsSyncBundle.message("notification.title.push.error"), pushResult.message)
        }
        SettingsSyncPushResult.Rejected -> {
          // todo add protection against potential infinite reject-update-reject cycle
          //  (it would indicate some problem, but still shouldn't cycle forever)

          // In the case of reject we'll just "wait" for the next update event:
          // it will be processed in the next session anyway
          if (pendingEvents.none { it is SyncSettingsEvent.CloudChange }) {
            // not to wait for too long, schedule an update right away unless it has already been scheduled
            updateChecker.scheduleUpdateFromServer()
          }
        }
      }
    }
    else if (pushToCloudRequested) {
      LOG.info("Requested to push if needed, but there was nothing to push")
    }
  }

  private fun pushToCloud(settingsSnapshot: SettingsSnapshot): SettingsSyncPushResult {
    val result = remoteCommunicator.push(settingsSnapshot)
    SettingsSyncStatusTracker.getInstance().updateStatus(result)
    return result
  }

  private fun pushToIde(settingsSnapshot: SettingsSnapshot): SettingsSyncPushResult {
    ideUpdater.settingsLogged(settingsSnapshot)
    return SettingsSyncPushResult.Success // todo
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

  companion object {
    private val LOG = logger<SettingsSyncBridge>()
  }
}