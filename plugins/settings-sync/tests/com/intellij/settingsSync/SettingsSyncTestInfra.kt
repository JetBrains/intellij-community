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
  val actualMap = this.fileStates.associate { it.file to String(it.content, StandardCharsets.UTF_8) }
  val expectedMap = settingsSnapshotBuilder.fileStates.associate { it.file to String(it.content, StandardCharsets.UTF_8) }
  Assert.assertEquals(expectedMap, actualMap)
}

internal fun PersistentStateComponent<*>.toFileState() : FileState {
  val file = PathManager.OPTIONS_DIRECTORY + "/" + getDefaultStoragePathSpec(this::class.java)
  val content = this.serialize()
  return FileState(file, content, content.size)
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
    fileState(FileState(file, byteArray, byteArray.size))
  }
}
