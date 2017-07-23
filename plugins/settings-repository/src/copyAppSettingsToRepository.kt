/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.configurationStore.getExportableComponentsMap
import com.intellij.configurationStore.removeMacroIfStartsWith
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.stateStore
import com.intellij.util.containers.forEachGuaranteed
import com.intellij.util.io.directoryStreamIfExists
import com.intellij.util.io.isFile
import com.intellij.util.io.readBytes
import com.intellij.util.io.systemIndependentPath
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

fun copyLocalConfig(storageManager: StateStorageManagerImpl = ApplicationManager.getApplication()!!.stateStore.stateStorageManager as StateStorageManagerImpl) {
  val streamProvider = storageManager.compoundStreamProvider.providers.first { it is IcsManager.IcsStreamProvider } as IcsManager.IcsStreamProvider

  val fileToItems = getExportableComponentsMap(true, false, storageManager)
  fileToItems.keys.forEachGuaranteed { file ->
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

    val roamingType = fileToItems.get(file)?.firstOrNull()?.roamingType ?: RoamingType.DEFAULT
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