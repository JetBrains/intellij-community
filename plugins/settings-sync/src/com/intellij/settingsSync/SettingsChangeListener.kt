package com.intellij.settingsSync

import java.time.Instant
import java.util.*

internal fun interface SettingsChangeListener : EventListener {

  fun settingChanged(event: SyncSettingsEvent)

}

internal sealed class SyncSettingsEvent {
  class IdeChange(val snapshot: SettingsSnapshot) : SyncSettingsEvent()
  class CloudChange(val snapshot: SettingsSnapshot) : SyncSettingsEvent()
  object MustPushRequest: SyncSettingsEvent()
  object LogCurrentSettings: SyncSettingsEvent()

  /**
   * Special request to ping the merge and push procedure in case there are settings which weren't pushed yet.
   */
  object PingRequest : SyncSettingsEvent()
}

internal data class SettingsSnapshot(val metaInfo: MetaInfo, val fileStates: Set<FileState>) {

  data class MetaInfo(val dateCreated: Instant)

  fun isEmpty(): Boolean = fileStates.isEmpty()
}

