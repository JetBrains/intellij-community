package com.intellij.settingsSync

import com.intellij.openapi.util.NlsSafe
import java.util.*

internal fun interface SettingsChangeListener : EventListener {

  fun settingChanged(event: SyncSettingsEvent)

}

internal sealed class SyncSettingsEvent {
  class IdeChange(snapshot: SettingsSnapshot) : EventWithSnapshot(snapshot)
  class CloudChange(snapshot: SettingsSnapshot, val serverVersionId: String?) : EventWithSnapshot(snapshot)
  object MustPushRequest : SyncSettingsEvent()
  object LogCurrentSettings : SyncSettingsEvent()

  /**
   * Special request to ping the merge and push procedure in case there are settings which weren't pushed yet.
   */
  object PingRequest : SyncSettingsEvent()

  /**
   * Tells that the settings sync has to be stopped, and the server data has to be deleted.
   * Other clients will disable sync as well, after they find that the data has been deleted.
   *
   * @param afterDeleting this callback function will be called after executing the deletion
   */
  class DeleteServerData(val afterDeleting: (DeleteServerDataResult) -> Unit): SyncSettingsEvent()

  /**
   * Indicates that the settings sync snapshot has been explicitly deleted on the server.
   * It means that other clients must disable settings sync.
   */
  object DeletedOnCloud: SyncSettingsEvent()

  override fun toString(): String {
    return javaClass.simpleName
  }

  internal sealed class EventWithSnapshot(val snapshot: SettingsSnapshot) : SyncSettingsEvent() {
    override fun toString(): String {
      return "${javaClass.simpleName}[${snapshot.fileStates.joinToString(limit = 5) { it.file }}]"
    }
  }
}

internal sealed class DeleteServerDataResult {
  object Success: DeleteServerDataResult()
  class Error(@NlsSafe val error: String): DeleteServerDataResult()
}
