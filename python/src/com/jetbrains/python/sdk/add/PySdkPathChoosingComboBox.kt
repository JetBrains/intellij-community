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
package com.jetbrains.python.sdk.add

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComponentWithBrowseButton
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ComboboxSpeedSearch
import com.intellij.util.PathUtil
import com.jetbrains.python.sdk.PyDetectedSdk
import com.jetbrains.python.sdk.PySdkListCellRenderer
import com.jetbrains.python.sdk.PythonSdkType
import javax.swing.JComboBox

/**
 * @author vlan
 */
class PySdkPathChoosingComboBox(sdks: List<Sdk>, suggestedFile: VirtualFile?) :
  ComponentWithBrowseButton<JComboBox<Sdk>>(JComboBox(sdks.toTypedArray()), null) {

  init {
    childComponent.apply {
      renderer = PySdkListCellRenderer(null)
      ComboboxSpeedSearch(this)
    }
    addActionListener {
      val pythonSdkType = PythonSdkType.getInstance()
      val descriptor = pythonSdkType.homeChooserDescriptor.apply {
        isForcedToUseIdeaFileChooser = true
      }
      FileChooser.chooseFiles(descriptor, null, suggestedFile) {
        val virtualFile = it.firstOrNull() ?: return@chooseFiles
        val path = PathUtil.toSystemDependentName(virtualFile.path)
        if (!pythonSdkType.isValidSdkHome(path)) return@chooseFiles
        childComponent.selectedItem =
          items.find { it.homePath == path } ?: PyDetectedSdk(path).apply {
            childComponent.insertItemAt(this, 0)
          }
      }
    }
  }

  var selectedSdk: Sdk?
    get() = childComponent.selectedItem as? Sdk?
    set(value) {
      if (value in items) {
        childComponent.selectedItem = value
      }
    }

  val items: List<Sdk>
    get() = (0 until childComponent.itemCount).map { childComponent.getItemAt(it) }
}