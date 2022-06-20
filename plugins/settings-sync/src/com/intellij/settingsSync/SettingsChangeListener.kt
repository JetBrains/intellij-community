package com.intellij.settingsSync

import java.time.Instant
import java.util.*

internal fun interface SettingsChangeListener : EventListener {

  fun settingChanged(event: SyncSettingsEvent)

}

internal sealed class SyncSettingsEvent {
  class IdeChange(val snapshot: SettingsSnapshot) : SyncSettingsEvent()
  class CloudChange(val snapshot: SettingsSnapshot) : SyncSettingsEvent()
  object PushIfNeededRequest : SyncSettingsEvent()
  object MustPushRequest: SyncSettingsEvent()
  object LogCurrentSettings: SyncSettingsEvent()
}

internal data class SettingsSnapshot(val metaInfo: MetaInfo, val fileStates: Set<FileState>) {

  data class MetaInfo(val dateCreated: Instant)

  fun isEmpty(): Boolean = fileStates.isEmpty()
}

