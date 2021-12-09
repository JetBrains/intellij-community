package com.intellij.settingsSync

import java.nio.charset.StandardCharsets.UTF_8

internal interface SettingsChangeListener {

  fun settingChanged(event: SettingsChangeEvent)

}

internal data class SettingsChangeEvent(val source: ChangeSource, val snapshot: SettingsSnapshot)

internal enum class ChangeSource {
  FROM_LOCAL,
  FROM_SERVER
}

internal interface SettingsLoggedListener {

  fun settingsLogged(event: SettingsLoggedEvent)
}

internal data class SettingsSnapshot(val fileStates: Set<FileState>) {
  fun isEmpty(): Boolean = fileStates.isEmpty()

  companion object {
    val EMPTY = SettingsSnapshot(emptySet())
  }
}

// todo use ByteArrayWrapper
internal data class FileState(val file: String, val content: ByteArray, val size: Int) {
  override fun toString(): String = "file='$file', content:\n${String(content, UTF_8)}"
}

internal class SettingsLoggedEvent(val snapshot: SettingsSnapshot, val hasLocal: Boolean, val hasRemote: Boolean, val conflicts: Set<Conflict>) {
  data class Conflict(val file: String, val appliedContent: ByteArray, val declinedContent: ByteArray)
}
