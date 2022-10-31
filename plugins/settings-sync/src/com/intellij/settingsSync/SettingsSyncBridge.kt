package com.intellij.settingsSync

import com.intellij.configurationStore.saveSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.settingsSync.SettingsSyncBridge.PushRequestMode.*
import com.intellij.util.Alarm
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Handles events about settings change both from the current IDE, and from the server, merges the settings, logs them,
 * and provides the combined data to clients: both to the IDE and to the server.
 */
@ApiStatus.Internal
class SettingsSyncBridge(parentDisposable: Disposable,
                         private val appConfigPath: Path,
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

  private val settingsChangeListener = SettingsChangeListener { event ->
    LOG.debug("Adding settings changed event $event to the queue")
    pendingEvents.add(event)
    queue.queue(updateObject)
  }

  @RequiresBackgroundThread
  internal fun initialize(initMode: InitMode) {
    saveIdeSettings()

    settingsLog.initialize()

    if (initMode.shouldEnableSync()) { // the queue is not activated initially => events will be collected but not processed until we perform all initialization tasks
      SettingsSyncEvents.getInstance().addSettingsChangedListener(settingsChangeListener)
      ideMediator.activateStreamProvider()
    }

    applyInitialChanges(initMode)

    if (initMode.shouldEnableSync()) {
      queue.activate()
    }
  }

  private fun saveIdeSettings() {
    runBlockingMaybeCancellable {
      saveSettings(ApplicationManager.getApplication(), forceSavingAllSettings = true)
    }
  }

  private fun applyInitialChanges(initMode: InitMode) {
    val previousState = collectCurrentState()

    settingsLog.logExistingSettings()

    try {
      when (initMode) {
        is InitMode.TakeFromServer -> applySnapshotFromServer(initMode.cloudEvent)
        InitMode.PushToServer -> mergeAndPush(previousState.idePosition, previousState.cloudPosition, FORCE_PUSH)
        InitMode.JustInit -> mergeAndPush(previousState.idePosition, previousState.cloudPosition, PUSH_IF_NEEDED)
        is InitMode.MigrateFromOldStorage -> migrateFromOldStorage(initMode.migration)
      }
    }
    catch (e: Throwable) {
      stopSyncingAndRollback(previousState, e)
    }
  }

  private fun applySnapshotFromServer(cloudEvent: SyncSettingsEvent.CloudChange) {
    settingsLog.advanceMaster() // merge (preserve) 'ide' changes made by logging existing settings

    val masterPosition = settingsLog.forceWriteToMaster(cloudEvent.snapshot, "Remote changes to initialize settings by data from cloud")
    pushToIde(settingsLog.collectCurrentSnapshot(), masterPosition)

    // normally we set cloud position only after successful push to cloud, but in this case we already take all settings from the cloud,
    // so no push is needed, and we know the cloud settings state.
    settingsLog.setCloudPosition(masterPosition)
    SettingsSyncLocalSettings.getInstance().knownAndAppliedServerId = cloudEvent.serverVersionId
  }

  private fun migrateFromOldStorage(migration: SettingsSyncMigration) {
    val migrationSnapshot = migration.getLocalDataIfAvailable(appConfigPath)
    if (migrationSnapshot != null) {
      settingsLog.applyIdeState(migrationSnapshot, "Migrate from old settings sync")
      LOG.info("Migration from old storage applied.")
      var masterPosition = settingsLog.advanceMaster() // merge (preserve) 'ide' changes made by logging existing settings & by migration

      // if there is already a version on the server, then it should be preferred over migration
      val updateResult = remoteCommunicator.receiveUpdates()
      if (updateResult is UpdateResult.Success) {
        val snapshot = updateResult.settingsSnapshot
        masterPosition = settingsLog.forceWriteToMaster(snapshot, "Remote changes to overwrite migration data by settings from cloud")
        SettingsSyncLocalSettings.getInstance().knownAndAppliedServerId = updateResult.serverVersionId
        pushToIde(settingsLog.collectCurrentSnapshot(), masterPosition)
      }
      else {
        if (migration.shouldEnableNewSync()) {
          forcePushToCloud(masterPosition) // otherwise we place our migrated data to the cloud
        }

        pushToIde(settingsLog.collectCurrentSnapshot(), masterPosition)
        migration.migrateCategoriesSyncStatus(appConfigPath, SettingsSyncSettings.getInstance())
        runBlocking {
          saveSettings(ApplicationManager.getApplication(), forceSavingAllSettings = true)
        }
        migration.executeAfterApplying()
      }
      settingsLog.setCloudPosition(masterPosition)
    }
    else {
      LOG.warn("Migration from old storage didn't happen, although it was identified as possible: no data to migrate")
      settingsLog.advanceMaster() // merge (preserve) 'ide' changes made by logging existing settings
    }
  }

  private fun forcePushToCloud(masterPosition: SettingsLog.Position) {
    pushAndHandleResult(true, masterPosition, onRejectedPush = {
      LOG.error("Reject shouldn't happen when force push is used")
      SettingsSyncStatusTracker.getInstance().updateOnError(SettingsSyncBundle.message("notification.title.push.error"))
    })
  }

  internal sealed class InitMode {
    object JustInit : InitMode()
    class TakeFromServer(val cloudEvent: SyncSettingsEvent.CloudChange) : InitMode()
    object PushToServer : InitMode()

    class MigrateFromOldStorage(val migration: SettingsSyncMigration) : InitMode() {
      override fun shouldEnableSync(): Boolean {
        return migration.shouldEnableNewSync()
      }
    }

    open fun shouldEnableSync(): Boolean = true
  }

  @RequiresBackgroundThread
  private fun processPendingEvents() {
    val previousState = collectCurrentState()

    try {
      var pushRequestMode: PushRequestMode = PUSH_IF_NEEDED
      var mergeAndPushAfterProcessingEvents = true
      while (pendingEvents.isNotEmpty()) {
        val event = pendingEvents.removeAt(0)
        LOG.debug("Processing event $event")
        when (event) {
          is SyncSettingsEvent.IdeChange -> {
            settingsLog.applyIdeState(event.snapshot, "Local changes made in the IDE")
          }
          is SyncSettingsEvent.CloudChange -> {
            settingsLog.applyCloudState(event.snapshot, "Remote changes")
            SettingsSyncLocalSettings.getInstance().knownAndAppliedServerId = event.serverVersionId
          }
          is SyncSettingsEvent.LogCurrentSettings -> {
            settingsLog.logExistingSettings()
          }
          is SyncSettingsEvent.MustPushRequest -> {
            pushRequestMode = MUST_PUSH
          }
          is SyncSettingsEvent.DeleteServerData -> {
            mergeAndPushAfterProcessingEvents = false
            stopSyncingAndRollback(previousState)
            deleteServerData(event.afterDeleting)
          }
          SyncSettingsEvent.DeletedOnCloud -> {
            mergeAndPushAfterProcessingEvents = false
            stopSyncingAndRollback(previousState)
          }
          SyncSettingsEvent.PingRequest -> {}
        }
      }

      if (mergeAndPushAfterProcessingEvents) {
        mergeAndPush(previousState.idePosition, previousState.cloudPosition, pushRequestMode)
      }
    }
    catch (exception: Throwable) {
      stopSyncingAndRollback(previousState, exception)
    }
  }

  private fun deleteServerData(afterDeleting: (DeleteServerDataResult) -> Unit) {
    val deletionSnapshot = SettingsSnapshot(SettingsSnapshot.MetaInfo(Instant.now(), getLocalApplicationInfo(), isDeleted = true),
                                            emptySet(), null)
    val pushResult = pushToCloud(deletionSnapshot, force = true)
    LOG.info("Deleting server data. Result: $pushResult")
    when (pushResult) {
      is SettingsSyncPushResult.Success -> {
        afterDeleting(DeleteServerDataResult.Success)
      }
      is SettingsSyncPushResult.Error -> {
        afterDeleting(DeleteServerDataResult.Error(pushResult.message))
      }
      SettingsSyncPushResult.Rejected -> {
        afterDeleting(DeleteServerDataResult.Error("Deletion rejected by server"))
      }
    }
  }

  private class CurrentState(
    val masterPosition: SettingsLog.Position,
    val idePosition: SettingsLog.Position,
    val cloudPosition: SettingsLog.Position,
    val knownServerId: String?
  )

  private fun collectCurrentState(): CurrentState = CurrentState(settingsLog.getMasterPosition(),
                                                                 settingsLog.getIdePosition(),
                                                                 settingsLog.getCloudPosition(),
                                                                 SettingsSyncLocalSettings.getInstance().knownAndAppliedServerId)

  private fun stopSyncingAndRollback(previousState: CurrentState, exception: Throwable? = null) {
    if (exception != null) {
      LOG.error("Couldn't apply settings. Disabling sync and rolling back.", exception)
    }
    else {
      LOG.info("Settings Sync is switched off. Rolling back.")
    }
    SettingsSyncSettings.getInstance().syncEnabled = false
    if (exception != null) {
      SettingsSyncStatusTracker.getInstance().updateOnError(exception.localizedMessage)
    }

    ideMediator.removeStreamProvider()
    SettingsSyncEvents.getInstance().removeSettingsChangedListener(settingsChangeListener)
    pendingEvents.clear()
    rollback(previousState)
    queue.deactivate() // for tests it is important to have it the last statement, otherwise waitForAllExecuted can finish before rollback
  }

  private fun rollback(previousState: CurrentState) {
    try {
      SettingsSyncLocalSettings.getInstance().knownAndAppliedServerId = previousState.knownServerId
      settingsLog.setIdePosition(previousState.idePosition)
      settingsLog.setCloudPosition(previousState.cloudPosition)
      settingsLog.setMasterPosition(previousState.masterPosition)
      // we don't need to roll back the state of the IDE here, because it is the latest stage of mergeAndPush which can fail
      // (pushing can fail also, but it is a normal failure which doesn't need to roll everything back and turn the sync off
    }
    catch (e: Throwable) {
      LOG.error("Couldn't rollback to the previous successful state", e)
    }
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
      pushAndHandleResult(pushRequestMode == FORCE_PUSH, masterPosition, onRejectedPush = {
        // todo add protection against potential infinite reject-update-reject cycle
        //  (it would indicate some problem, but still shouldn't cycle forever)

        // In the case of reject we'll just "wait" for the next update event:
        // it will be processed in the next session anyway
        if (pendingEvents.none { it is SyncSettingsEvent.CloudChange }) {
          // not to wait for too long, schedule an update right away unless it has already been scheduled
          updateChecker.scheduleUpdateFromServer()
        }
      })
    }
    else {
      LOG.debug("Nothing to push")
    }
  }

  private fun pushAndHandleResult(force: Boolean, positionToSetCloudBranch: SettingsLog.Position, onRejectedPush: () -> Unit) {
    val pushResult: SettingsSyncPushResult = pushToCloud(settingsLog.collectCurrentSnapshot(), force)
    LOG.info("Result of pushing settings to the cloud: $pushResult")
    when (pushResult) {
      is SettingsSyncPushResult.Success -> {
        settingsLog.setCloudPosition(positionToSetCloudBranch)
        SettingsSyncLocalSettings.getInstance().knownAndAppliedServerId = pushResult.serverVersionId
        SettingsSyncStatusTracker.getInstance().updateOnSuccess()
      }
      is SettingsSyncPushResult.Error -> {
        SettingsSyncStatusTracker.getInstance().updateOnError(
          SettingsSyncBundle.message("notification.title.push.error") + ": " + pushResult.message)
      }
      SettingsSyncPushResult.Rejected -> {
        onRejectedPush()
      }
    }
  }

  private enum class PushRequestMode {
    PUSH_IF_NEEDED,
    MUST_PUSH,
    FORCE_PUSH
  }

  private fun pushToCloud(settingsSnapshot: SettingsSnapshot, force: Boolean): SettingsSyncPushResult {
    val versionId = SettingsSyncLocalSettings.getInstance().knownAndAppliedServerId
    if (force) {
      return remoteCommunicator.push(settingsSnapshot, force = true, versionId)
    }
    else if (remoteCommunicator.checkServerState() is ServerState.UpdateNeeded) {
      return SettingsSyncPushResult.Rejected
    }
    else {
      return remoteCommunicator.push(settingsSnapshot, force = false, versionId)
    }
  }

  private fun pushToIde(settingsSnapshot: SettingsSnapshot, targetPosition: SettingsLog.Position) {
    ideMediator.applyToIde(settingsSnapshot)
    settingsLog.setIdePosition(targetPosition)
    LOG.info("Applied settings to the IDE.")
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