package com.intellij.settingsSync

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus.Internal


@Internal
sealed class SyncSettingsEvent {
  /**
   * These events are processed in a batch
   */
  sealed class StandardEvent: SyncSettingsEvent()

  /**
   * Exclusive events are processed separately from other events.
   */
  sealed class ExclusiveEvent : SyncSettingsEvent()

  class IdeChange(snapshot: SettingsSnapshot) : EventWithSnapshot(snapshot)
  class CloudChange(snapshot: SettingsSnapshot, val serverVersionId: String?, val syncSettings: SettingsSyncState? = null)
    : EventWithSnapshot(snapshot)
  object MustPushRequest : StandardEvent()
  object LogCurrentSettings : StandardEvent()

  /**
   * Request to check the server state and the local state, and initiate the sync procedure, if there is a newer version on the server,
   * or if there are changes which were not pushed yet.
   */
  object SyncRequest : ExclusiveEvent()

  /**
   * Tells that the settings sync has to be stopped, and the server data has to be deleted.
   * Other clients will disable sync as well, after they find that the data has been deleted.
   *
   * @param afterDeleting this callback function will be called after executing the deletion
   */
  class DeleteServerData(val afterDeleting: (DeleteServerDataResult) -> Unit): StandardEvent()

  /**
   * Indicates that the settings sync snapshot has been explicitly deleted on the server.
   * It means that other clients must disable settings sync.
   */
  object DeletedOnCloud: StandardEvent()

  class CrossIdeSyncStateChanged(val isCrossIdeSyncEnabled: Boolean) : ExclusiveEvent()

  class RestoreSettingsSnapshot(val hash: String, val onComplete: Runnable): ExclusiveEvent()

  override fun toString(): String {
    return javaClass.simpleName
  }

  sealed class EventWithSnapshot(val snapshot: SettingsSnapshot) : StandardEvent() {
    override fun toString(): String {
      return "${javaClass.simpleName}[${snapshot.fileStates.joinToString(limit = 5) { it.file }}]"
    }
  }
}

@Internal
sealed class DeleteServerDataResult {
  object Success: DeleteServerDataResult()
  class Error(@NlsSafe val error: String): DeleteServerDataResult()
}
