package com.intellij.settingsSync

import java.util.*

internal fun interface SettingsChangeListener : EventListener {

  fun settingChanged(event: SyncSettingsEvent)

}

internal sealed class SyncSettingsEvent {
  class IdeChange(val snapshot: SettingsSnapshot) : SyncSettingsEvent()
  class CloudChange(val snapshot: SettingsSnapshot) : SyncSettingsEvent()
  object PushIfNeededRequest : SyncSettingsEvent()
  object MustPushRequest: SyncSettingsEvent()
}

internal data class SettingsSnapshot(val fileStates: Set<FileState>) {
  fun isEmpty(): Boolean = fileStates.isEmpty()
}

