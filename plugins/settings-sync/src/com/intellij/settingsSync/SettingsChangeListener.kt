package com.intellij.settingsSync

import java.nio.charset.StandardCharsets.UTF_8

internal interface SettingsChangeListener {

  fun settingChanged(event: SyncSettingsEvent)

}

internal sealed class SyncSettingsEvent {
  class IdeChange(val snapshot: SettingsSnapshot) : SyncSettingsEvent()
  class CloudChange(val snapshot: SettingsSnapshot) : SyncSettingsEvent()
  object PushRequest : SyncSettingsEvent()
}

internal data class SettingsSnapshot(val fileStates: Set<FileState>) {
  fun isEmpty(): Boolean = fileStates.isEmpty()
}

// todo use ByteArrayWrapper
internal data class FileState(val file: String, val content: ByteArray, val size: Int) {
  override fun toString(): String = "file='$file', content:\n${String(content, UTF_8)}"
}
