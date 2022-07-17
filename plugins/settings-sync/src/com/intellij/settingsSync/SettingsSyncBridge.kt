package com.intellij.settingsSync

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.settingsSync.SettingsSyncBridge.PushRequestMode.*
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
                                  private val ideMediator: SettingsSyncIdeMediator,
                                  private val remoteCommunicator: SettingsSyncRemoteCommunicator,
                                  private val updateChecker: SettingsSyncUpdateChecker) {

  private val pendingEvents = ContainerUtil.createConcurrentList<SyncSettingsEvent>()

  private val queue = MergingUpdateQueue("SettingsSyncBridge", 1000, false, null, parentDisposable, null,
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

    // the queue is not activated initially => events will be collected but not processed until we perform all initialization tasks
    SettingsSyncEvents.getInstance().addSettingsChangedListener { event ->
      pendingEvents.add(event)
      queue.queue(updateObject)
    }
    ideMediator.activateStreamProvider()

    applyInitialChanges(initMode)

    queue.activate()
  }

  private fun applyInitialChanges(initMode: InitMode) {
    val previousIdePosition = settingsLog.getIdePosition()
    val previousCloudPosition = settingsLog.getCloudPosition()

    settingsLog.logExistingSettings()

    when (initMode) {
      is InitMode.TakeFromServer -> applySnapshotFromServer(initMode.cloudEvent.snapshot)
      InitMode.PushToServer -> mergeAndPush(previousIdePosition, previousCloudPosition, FORCE_PUSH)
      InitMode.JustInit -> mergeAndPush(previousIdePosition, previousCloudPosition, PUSH_IF_NEEDED)
    }
  }

  private fun applySnapshotFromServer(settingsSnapshot: SettingsSnapshot) {
    settingsLog.advanceMaster() // merge (preserve) 'ide' changes made by logging existing settings

    val masterPosition = settingsLog.forceWriteToMaster(settingsSnapshot)
    pushToIde(settingsLog.collectCurrentSnapshot(), masterPosition)

    // normally we set cloud position only after successful push to cloud, but in this case we already take all settings from the cloud,
    // so no push is needed, and we know the cloud settings state.
    settingsLog.setCloudPosition(masterPosition)
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

    var pushRequestMode: PushRequestMode = PUSH_IF_NEEDED
    while (pendingEvents.isNotEmpty()) {
      val event = pendingEvents.removeAt(0)
      if (event is SyncSettingsEvent.IdeChange) {
        settingsLog.applyIdeState(event.snapshot)
      }
      else if (event is SyncSettingsEvent.CloudChange) {
        settingsLog.applyCloudState(event.snapshot)
      }
      else if (event is SyncSettingsEvent.LogCurrentSettings) {
        settingsLog.logExistingSettings()
      }
      else if (event is SyncSettingsEvent.MustPushRequest) {
        pushRequestMode = MUST_PUSH
      }
    }

    mergeAndPush(previousIdePosition, previousCloudPosition, pushRequestMode)
  }

  private fun mergeAndPush(previousIdePosition: SettingsLog.Position,
                           previousCloudPosition: SettingsLog.Position,
                           pushRequestMode: PushRequestMode) {
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
      pushToIde(settingsLog.collectCurrentSnapshot(), masterPosition)
    }

    if (newCloudPosition != masterPosition || pushRequestMode == MUST_PUSH || pushRequestMode == FORCE_PUSH) {
      val pushResult: SettingsSyncPushResult = pushToCloud(settingsLog.collectCurrentSnapshot(), pushRequestMode == FORCE_PUSH)
      LOG.info("Result of pushing settings to the cloud: $pushResult")
      when (pushResult) {
        SettingsSyncPushResult.Success -> {
          settingsLog.setCloudPosition(masterPosition)
          SettingsSyncStatusTracker.getInstance().updateOnSuccess()
        }
        is SettingsSyncPushResult.Error -> {
          SettingsSyncStatusTracker.getInstance().updateOnError(
            SettingsSyncBundle.message("notification.title.push.error") + ": " + pushResult.message)
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
    else {
      LOG.info("Nothing to push")
    }
  }

  private enum class PushRequestMode {
    PUSH_IF_NEEDED,
    MUST_PUSH,
    FORCE_PUSH
  }

  private fun pushToCloud(settingsSnapshot: SettingsSnapshot, force: Boolean): SettingsSyncPushResult {
    if (force) {
      return remoteCommunicator.push(settingsSnapshot, force = true)
    }
    else if (remoteCommunicator.checkServerState() is ServerState.UpdateNeeded) {
      return SettingsSyncPushResult.Rejected
    }
    else {
      return remoteCommunicator.push(settingsSnapshot, force = false)
    }
  }

  private fun pushToIde(settingsSnapshot: SettingsSnapshot, targetPosition: SettingsLog.Position) {
    try {
      ideMediator.applyToIde(settingsSnapshot)
      settingsLog.setIdePosition(targetPosition)
      LOG.info("Applied settings to the IDE.")
    }
    catch (e: Throwable) {
      LOG.error(e)
      SettingsSyncStatusTracker.getInstance().updateOnError(SettingsSyncBundle.message("notification.title.apply.error") + ": " + e.message)
    }
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