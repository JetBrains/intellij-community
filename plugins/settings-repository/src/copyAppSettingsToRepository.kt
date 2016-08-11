/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.configurationStore.StreamProviderWrapper
import com.intellij.configurationStore.removeMacroIfStartsWith
import com.intellij.ide.actions.ExportableItem
import com.intellij.ide.actions.getExportableComponentsMap
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.stateStore
import com.intellij.util.containers.forEachGuaranteed
import com.intellij.util.directoryStreamIfExists
import com.intellij.util.isFile
import com.intellij.util.readBytes
import com.intellij.util.systemIndependentPath
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

fun copyLocalConfig(storageManager: StateStorageManagerImpl = ApplicationManager.getApplication()!!.stateStore.stateStorageManager as StateStorageManagerImpl) {
  val streamProvider = StreamProviderWrapper.getOriginalProvider(storageManager.streamProvider)!! as IcsManager.IcsStreamProvider

  val fileToComponents = getExportableComponentsMap(true, false, storageManager)
  fileToComponents.keys.forEachGuaranteed { file ->
    var fileSpec: String
    try {
      val absolutePath = file.toAbsolutePath().systemIndependentPath
      fileSpec = removeMacroIfStartsWith(storageManager.collapseMacros(absolutePath), ROOT_CONFIG)
      if (fileSpec == absolutePath) {
        // we have not experienced such problem yet, but we are just aware
        val canonicalPath = file.toRealPath().systemIndependentPath
        if (canonicalPath != absolutePath) {
          fileSpec = removeMacroIfStartsWith(storageManager.collapseMacros(canonicalPath), ROOT_CONFIG)
        }
      }
    }
    catch (e: NoSuchFileException) {
      return@forEachGuaranteed
    }

    val roamingType = getRoamingType(fileToComponents.get(file)!!)
    if (file.isFile()) {
      val fileBytes = file.readBytes()
      streamProvider.doSave(fileSpec, fileBytes, fileBytes.size, roamingType)
    }
    else {
      saveDirectory(file, fileSpec, roamingType, streamProvider)
    }
  }
}

private fun saveDirectory(parent: Path, parentFileSpec: String, roamingType: RoamingType, streamProvider: IcsManager.IcsStreamProvider) {
  parent.directoryStreamIfExists {
    for (file in it) {
      val childFileSpec = "$parentFileSpec/${file.fileName}"
      if (file.isFile()) {
        val fileBytes = Files.readAllBytes(file)
        streamProvider.doSave(childFileSpec, fileBytes, fileBytes.size, roamingType)
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