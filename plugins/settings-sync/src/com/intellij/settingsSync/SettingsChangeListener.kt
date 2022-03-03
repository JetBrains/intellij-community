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

internal data class FileState(val file: String, val content: ByteArray, val size: Int) {
  override fun toString(): String = "file='$file', content:\n${String(content, UTF_8)}"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as FileState

    if (file != other.file) return false
    if (!content.contentEquals(other.content)) return false
    if (size != other.size) return false

    return true
  }

  override fun hashCode(): Int {
    var result = file.hashCode()
    result = 31 * result + content.contentHashCode()
    result = 31 * result + size
    return result
  }
}
