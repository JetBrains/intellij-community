package com.intellij.settingsSync

import com.intellij.configurationStore.getDefaultStoragePathSpec
import com.intellij.configurationStore.serializeStateInto
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.util.toByteArray
import com.intellij.util.xmlb.Constants
import org.jdom.Element
import org.junit.Assert
import java.nio.charset.StandardCharsets

internal fun SettingsSnapshot.assertSettingsSnapshot(build: SettingsSnapshotBuilder.() -> Unit) {
  val settingsSnapshotBuilder = SettingsSnapshotBuilder()
  settingsSnapshotBuilder.build()
  val transformation = { fileState: FileState ->
    val content = if (fileState is FileState.Modified) String(fileState.content, StandardCharsets.UTF_8) else DELETED_FILE_MARKER
    fileState.file to content
  }
  val actualMap = this.fileStates.associate(transformation)
  val expectedMap = settingsSnapshotBuilder.fileStates.associate(transformation)
  Assert.assertEquals(expectedMap, actualMap)
}

internal fun PersistentStateComponent<*>.toFileState() : FileState {
  val file = PathManager.OPTIONS_DIRECTORY + "/" + getDefaultStoragePathSpec(this::class.java)
  val content = this.serialize()
  return FileState.Modified(file, content, content.size)
}

internal val <T> PersistentStateComponent<T>.name: String
  get() = (this::class.annotations.find { it is State } as? State)?.name!!

internal fun PersistentStateComponent<*>.serialize(): ByteArray {
  val compElement = Element("component")
  compElement.setAttribute(Constants.NAME, this.name)
  serializeStateInto(this, compElement)

  val appElement = Element("application")
  appElement.addContent(compElement)
  return appElement.toByteArray()
}

internal fun settingsSnapshot(build: SettingsSnapshotBuilder.() -> Unit) : SettingsSnapshot {
  val builder = SettingsSnapshotBuilder()
  builder.build()
  return SettingsSnapshot(builder.fileStates.toSet())
}

internal class SettingsSnapshotBuilder {
  val fileStates = mutableListOf<FileState>()

  fun fileState(function: () -> PersistentStateComponent<*>) {
    val component : PersistentStateComponent<*> = function()
    fileStates.add(component.toFileState())
  }

  fun fileState(fileState: FileState) {
    fileStates.add(fileState)
  }

  fun fileState(file: String, content: String) {
    val byteArray = content.toByteArray()
    fileState(FileState.Modified(file, byteArray, byteArray.size))
  }
}
