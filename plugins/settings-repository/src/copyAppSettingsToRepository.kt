package org.jetbrains.settingsRepository

import com.intellij.ide.actions.ExportSettingsAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.util.io.FileUtil
import java.io.File

fun copyLocalConfig() {
  val stateStorageManager = ApplicationManager.getApplication()!!.stateStore.getStateStorageManager()
  val streamProvider = stateStorageManager.getStreamProvider()!! as IcsManager.IcsStreamProvider

  val fileToComponents = ExportSettingsAction.getExportableComponentsMap(true, false)
  for (file in fileToComponents.keySet()) {
    val absolutePath = file.getAbsolutePath()
    var fileSpec = stateStorageManager.collapseMacros(absolutePath)
    if (fileSpec.equals(absolutePath)) {
      // we have not experienced such problem yet, but we are just aware
      val canonicalPath = file.getCanonicalPath()
      if (!canonicalPath.equals(absolutePath)) {
        fileSpec = stateStorageManager.collapseMacros(canonicalPath)
      }
    }

    val roamingType = getRoamingType(fileToComponents.get(file))
    if (file.isFile()) {
      val fileBytes = FileUtil.loadFileBytes(file)
      streamProvider.doSave(fileSpec, fileBytes, fileBytes.size(), roamingType)
    }
    else {
      saveDirectory(file, fileSpec, roamingType, streamProvider)
    }
  }
}

private fun saveDirectory(parent: File, parentFileSpec: String, roamingType: RoamingType, streamProvider: IcsManager.IcsStreamProvider) {
  val files = parent.listFiles()
  if (files != null) {
    for (file in files) {
      val childFileSpec = parentFileSpec + '/' + file.getName()
      if (file.isFile()) {
        val fileBytes = FileUtil.loadFileBytes(file)
        streamProvider.doSave(childFileSpec, fileBytes, fileBytes.size(), roamingType)
      }
      else {
        saveDirectory(file, childFileSpec, roamingType, streamProvider)
      }
    }
  }
}

private fun getRoamingType(components: Collection<ExportableComponent>): RoamingType {
  for (component in components) {
    if (component is ExportSettingsAction.ExportableComponentItem) {
      return component.getRoamingType()
    }
    else if (component is PersistentStateComponent<*>) {
      val stateAnnotation = component.javaClass.getAnnotation(javaClass<State>())
      if (stateAnnotation != null) {
        val storages = stateAnnotation.storages
        if (!storages.isEmpty()) {
          return storages[0].roamingType
        }
      }
    }
  }
  return RoamingType.PER_USER
}