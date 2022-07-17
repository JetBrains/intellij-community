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

import com.intellij.execution.Platform
import com.intellij.execution.target.BrowsableTargetEnvironmentType
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentType
import com.intellij.execution.target.getTargetType
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBoxWithWidePopup
import com.intellij.openapi.ui.ComponentWithBrowseButton
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ComboboxSpeedSearch
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.util.PathUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.add.target.createDetectedSdk
import com.jetbrains.python.ui.ManualPathEntryDialog
import java.awt.event.ActionListener
import java.util.function.Supplier
import javax.swing.JComboBox
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
                                                          private val newPySdkComboBoxItem: NewPySdkComboBoxItem? = null,
                                                          private val targetEnvironmentConfiguration: TargetEnvironmentConfiguration? = null) :
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
    val actionListener: ActionListener =
      if (targetEnvironmentConfiguration == null) {
        // Local FS chooser
        ActionListener {
          val pythonSdkType = PythonSdkType.getInstance()
          val descriptor = pythonSdkType.homeChooserDescriptor
          FileChooser.chooseFiles(descriptor, null, suggestedFile) {
            val virtualFile = it.firstOrNull() ?: return@chooseFiles
            val path = PathUtil.toSystemDependentName(virtualFile.path)
            selectedSdk =
              items.find { it.homePath == path } ?: createDetectedSdk(path, isLocal = true).apply {
                addSdkItemOnTop(this)
              }
          }
        }
      }
      else {
        val targetType: TargetEnvironmentType<*> = targetEnvironmentConfiguration.getTargetType()
        if (targetType is BrowsableTargetEnvironmentType) {
          val project = ProjectManager.getInstance().defaultProject
          val title = PyBundle.message("python.sdk.interpreter.executable.path.title")
          targetType.createBrowser(project,
                                   title,
                                   PY_SDK_COMBOBOX_TEXT_ACCESSOR,
                                   childComponent,
                                   Supplier { targetEnvironmentConfiguration })
        }
        else {
          // The fallback where the path is entered manually
          ActionListener {
            val dialog = ManualPathEntryDialog(project = null, platform = Platform.UNIX)
            if (dialog.showAndGet()) {
              childComponent.selectedItem = createDetectedSdk(dialog.path, isLocal = false).apply { addSdkItemOnTop(this) }
            }
          }
        }
      }
    addActionListener(actionListener)
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
    private val PY_SDK_COMBOBOX_TEXT_ACCESSOR = object : TextComponentAccessor<JComboBox<PySdkComboBoxItem>> {
      override fun getText(component: JComboBox<PySdkComboBoxItem>): String =
        (component.selectedItem as? ExistingPySdkComboBoxItem)?.getText().orEmpty()

      override fun setText(component: JComboBox<PySdkComboBoxItem>, text: String) {
        val newItem = ExistingPySdkComboBoxItem(createDetectedSdk(text, isLocal = false))
        component.addItem(newItem)
        component.selectedItem = newItem
      }
    }

    private fun buildSdkArray(sdks: List<Sdk>, newPySdkComboBoxItem: NewPySdkComboBoxItem?): Array<PySdkComboBoxItem> =
      (listOfNotNull(newPySdkComboBoxItem) + sdks.map { it.asComboBoxItem() }).toTypedArray()

    private fun PySdkComboBoxItem.getText(): String = when (this) {
      is NewPySdkComboBoxItem -> title
      is ExistingPySdkComboBoxItem -> sdk.homePath.orEmpty()
    }
  }
}
