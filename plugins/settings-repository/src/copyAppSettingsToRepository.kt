package org.jetbrains.settingsRepository

import com.intellij.openapi.application.ApplicationManager
import com.intellij.ide.actions.ExportSettingsAction
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import java.io.File
import com.intellij.openapi.components.ExportableComponent
import com.intellij.util.containers.MultiMap
import com.intellij.openapi.util.RoamingTypeDisabled
import com.intellij.openapi.components.impl.stores.StreamProvider

fun copyLocalConfig() {
  val application = ApplicationManager.getApplication()!! as ApplicationImpl
  application.saveSettings()

  val stateStorageManager = application.getStateStore().getStateStorageManager()
  val streamProvider = stateStorageManager.getStreamProvider()!!

  val fileToComponents = ExportSettingsAction.getExportableComponentsMap()
  for (file in fileToComponents.keySet()) {
    if (!file.exists()) {
      continue
    }

    val roamingType = getRoamingType(file, fileToComponents)
    if (roamingType == RoamingType.DISABLED) {
      continue
    }

    val absolutePath = file.getAbsolutePath()
    var fileSpec = stateStorageManager.collapseMacros(absolutePath)
    if (fileSpec.equals(absolutePath)) {
      // we have not experienced such problem yet, but we are just aware
      val canonicalPath = file.getCanonicalPath()
      if (!canonicalPath.equals(absolutePath)) {
        fileSpec = stateStorageManager.collapseMacros(canonicalPath)
      }
    }

    if (file.isFile()) {
      val fileBytes = FileUtil.loadFileBytes(file)
      streamProvider.saveContent(fileSpec, fileBytes, fileBytes.size, roamingType, false)
    }
    else {
      saveDirectory(file, fileSpec, roamingType, streamProvider)
    }
  }
}

private fun saveDirectory(parent: File, parentFileSpec: String, roamingType: RoamingType, streamProvider: StreamProvider) {
  val files = parent.listFiles()
  if (files != null) {
    for (file in files) {
      val childFileSpec = parentFileSpec + '/' + file.getName()
      if (file.isFile()) {
        val fileBytes = FileUtil.loadFileBytes(file)
        streamProvider.saveContent(childFileSpec, fileBytes, fileBytes.size, roamingType, false)
      }
      else {
        saveDirectory(file, childFileSpec, roamingType, streamProvider)
      }
    }
  }
}

private fun getRoamingType(file: File, fileToComponents: MultiMap<File, ExportableComponent>): RoamingType {
  for (component in fileToComponents.get(file)) {
    if (component is PersistentStateComponent<*>) {
      val stateAnnotation = component.javaClass.getAnnotation(javaClass<State>())
      if (stateAnnotation != null) {
        val storages = stateAnnotation.storages()
        if (!storages.isEmpty()) {
          return storages[0].roamingType()
        }
      }
    }
    else if (component is RoamingTypeDisabled) {
      return RoamingType.DISABLED
    }
  }
  return RoamingType.PER_USER
}