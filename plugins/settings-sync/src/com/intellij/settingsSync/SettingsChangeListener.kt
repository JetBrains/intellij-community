package com.intellij.settingsSync

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

  override fun toString(): String {
    return javaClass.simpleName
  }

  internal sealed class EventWithSnapshot(val snapshot: SettingsSnapshot) : SyncSettingsEvent() {
    override fun toString(): String {
      return "${javaClass.simpleName}[${snapshot.fileStates.joinToString(limit = 5) { it.file }}]"
    }
  }
}

