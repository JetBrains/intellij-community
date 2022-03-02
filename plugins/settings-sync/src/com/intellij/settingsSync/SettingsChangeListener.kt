package com.intellij.settingsSync

internal interface SettingsChangeListener {

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

