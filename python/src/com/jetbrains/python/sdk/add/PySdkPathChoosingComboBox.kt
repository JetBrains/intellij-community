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
import com.intellij.openapi.ui.ComboBoxWithWidePopup
import com.intellij.openapi.ui.ComponentWithBrowseButton
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ComboboxSpeedSearch
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.util.PathUtil
import com.jetbrains.python.sdk.PyDetectedSdk
import com.jetbrains.python.sdk.PythonSdkType
import java.awt.event.ActionListener
import javax.swing.plaf.basic.BasicComboBoxEditor

/**
 * A combobox with browse button for choosing a path to SDK, also capable of showing progress indicator.
 * To toggle progress indicator visibility use [setBusy] method.
 *
 * To fill this box in async mode use [addInterpretersAsync]
 *
 * @author vlan
 */
class PySdkPathChoosingComboBox @JvmOverloads constructor(sdks: List<Sdk> = emptyList(),
                                                          suggestedFile: VirtualFile? = null,
                                                          private val newPySdkComboBoxItem: NewPySdkComboBoxItem? = null) :
  ComponentWithBrowseButton<ComboBoxWithWidePopup<PySdkComboBoxItem>>(ComboBoxWithWidePopup(buildSdkArray(sdks, newPySdkComboBoxItem)),
                                                                      null) {

  private val busyIconExtension: ExtendableTextComponent.Extension = ExtendableTextComponent.Extension { AnimatedIcon.Default.INSTANCE }
  private val editor: BasicComboBoxEditor = object : BasicComboBoxEditor() {
    override fun createEditorComponent() = ExtendableTextField().apply {
      isEditable = false
    }
  }

  init {
    childComponent.apply {
      renderer = PySdkListCellRendererExt()
      ComboboxSpeedSearch(this)
    }
    // prepare action listener
    addActionListener(ActionListener {
      val pythonSdkType = PythonSdkType.getInstance()
      val descriptor = pythonSdkType.homeChooserDescriptor
      FileChooser.chooseFiles(descriptor, null, suggestedFile) {
        val virtualFile = it.firstOrNull() ?: return@chooseFiles
        val path = PathUtil.toSystemDependentName(virtualFile.path)
        childComponent.selectedItem =
          items.find { it.homePath == path } ?: PyDetectedSdk(path).apply {
            addSdkItemOnTop(this)
          }
      }
    })
  }

  val selectedItem: PySdkComboBoxItem?
    get() = childComponent.selectedItem as? PySdkComboBoxItem

  var selectedSdk: Sdk?
    get() = (childComponent.selectedItem as? ExistingPySdkComboBoxItem)?.sdk
    /**
     * Does nothing if [selectedSdk] is absent in the items in the combobox.
     */
    set(value) {
      (0 until childComponent.itemCount)
        .map { childComponent.getItemAt(it) }
        .firstOrNull { (it as? ExistingPySdkComboBoxItem)?.sdk == value }
        ?.let { childComponent.selectedItem = it }
    }

  val items: List<Sdk>
    get() = (0 until childComponent.itemCount).mapNotNull { (childComponent.getItemAt(it) as? ExistingPySdkComboBoxItem)?.sdk }

  fun addSdkItemOnTop(sdk: Sdk) {
    val position = if (newPySdkComboBoxItem == null) 0 else 1
    childComponent.insertItemAt(sdk.asComboBoxItem(), position)
  }

  fun addSdkItem(sdk: Sdk) {
    childComponent.addItem(sdk.asComboBoxItem())
  }

  fun setBusy(busy: Boolean) {
    if (busy) {
      childComponent.isEditable = true
      childComponent.editor = editor
      (childComponent.editor.editorComponent as ExtendableTextField).addExtension(busyIconExtension)
    }
    else {
      (childComponent.editor.editorComponent as ExtendableTextField).removeExtension(busyIconExtension)
      childComponent.isEditable = false
    }
    repaint()
  }

  companion object {
    private fun buildSdkArray(sdks: List<Sdk>, newPySdkComboBoxItem: NewPySdkComboBoxItem?): Array<PySdkComboBoxItem> =
      (listOfNotNull(newPySdkComboBoxItem) + sdks.map { it.asComboBoxItem() }).toTypedArray()

    private fun PySdkComboBoxItem.getText(): String = when (this) {
      is NewPySdkComboBoxItem -> title
      is ExistingPySdkComboBoxItem -> sdk.homePath.orEmpty()
    }
  }
}
