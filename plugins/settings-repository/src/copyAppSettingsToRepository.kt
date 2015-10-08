/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.settingsRepository

import com.intellij.configurationStore.ROOT_CONFIG
import com.intellij.configurationStore.StateStorageManagerImpl
import com.intellij.ide.actions.ExportableItem
import com.intellij.ide.actions.getExportableComponentsMap
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import java.io.File

fun copyLocalConfig(storageManager: StateStorageManagerImpl = ApplicationManager.getApplication()!!.stateStore.stateStorageManager as StateStorageManagerImpl) {
  val streamProvider = storageManager.streamProvider!! as IcsManager.IcsStreamProvider

  val fileToComponents = getExportableComponentsMap(true, false, storageManager)
  for (file in fileToComponents.keySet()) {
    val absolutePath = FileUtilRt.toSystemIndependentName(file.absolutePath)
    var fileSpec = storageManager.collapseMacros(absolutePath)
    LOG.assertTrue(!fileSpec.contains(ROOT_CONFIG))
    if (fileSpec.equals(absolutePath)) {
      // we have not experienced such problem yet, but we are just aware
      val canonicalPath = FileUtilRt.toSystemIndependentName(file.canonicalPath)
      if (!canonicalPath.equals(absolutePath)) {
        fileSpec = storageManager.collapseMacros(canonicalPath)
      }
    }

    val roamingType = getRoamingType(fileToComponents.get(file)!!)
    if (file.isFile) {
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
      val childFileSpec = parentFileSpec + '/' + file.name
      if (file.isFile) {
        val fileBytes = FileUtil.loadFileBytes(file)
        streamProvider.doSave(childFileSpec, fileBytes, fileBytes.size(), roamingType)
      }
      else {
        saveDirectory(file, childFileSpec, roamingType, streamProvider)
      }
    }
  }
}

private fun getRoamingType(components: Collection<ExportableItem>): RoamingType {
  for (component in components) {
    if (component is ExportableItem) {
      return component.roamingType
    }
//    else if (component is PersistentStateComponent<*>) {
//      val stateAnnotation = component.javaClass.getAnnotation(State::class.java)
//      if (stateAnnotation != null) {
//        val storages = stateAnnotation.storages
//        if (!storages.isEmpty()) {
//          return storages[0].roamingType
//        }
//      }
//    }
  }
  return RoamingType.DEFAULT
}