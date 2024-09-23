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

import com.intellij.execution.target.*
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
import com.jetbrains.python.sdk.add.v1.PySdkListCellRendererExt
import com.jetbrains.python.sdk.add.v1.asComboBoxItem
import com.jetbrains.python.sdk.add.v1.createDetectedSdk
import com.jetbrains.python.ui.targetPathEditor.ManualPathEntryDialog
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
 */
class PySdkPathChoosingComboBox @JvmOverloads constructor(
  sdks: List<Sdk> = emptyList(),
  suggestedFile: VirtualFile? = null,
  private val newPySdkComboBoxItem: NewPySdkComboBoxItem? = null,
  targetEnvironmentConfiguration: TargetEnvironmentConfiguration? = null,
) :
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
      ComboboxSpeedSearch.installOn(this)
    }
    addActionListener(createBrowseActionListener(suggestedFile, targetEnvironmentConfiguration))
  }

  private fun createBrowseActionListener(suggestedFile: VirtualFile?, targetEnvironmentConfiguration: TargetEnvironmentConfiguration?) =
    if (targetEnvironmentConfiguration == null) {
      // Local FS chooser
      ActionListener {
        val pythonSdkType = PythonSdkType.getInstance()
        val descriptor = pythonSdkType.homeChooserDescriptor
        FileChooser.chooseFiles(descriptor, null, suggestedFile) { chosenFiles ->
          val virtualFile = chosenFiles.firstOrNull() ?: return@chooseFiles
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
                                 PySdkComboBoxTextAccessor(targetEnvironmentConfiguration),
                                 childComponent,
                                 Supplier { targetEnvironmentConfiguration },
                                 TargetBrowserHints(false))
      }
      else {
        // The fallback where the path is entered manually
        ActionListener {
          val dialog = ManualPathEntryDialog(project = null, targetEnvironmentConfiguration)
          if (dialog.showAndGet()) {
            childComponent.selectedItem = createDetectedSdk(dialog.path, targetEnvironmentConfiguration).apply { addSdkItemOnTop(this) }
          }
        }
      }
    }

  val selectedItem: PySdkComboBoxItem?
    get() = childComponent.selectedItem as? PySdkComboBoxItem

  internal val selectedSdkIfExists: Sdk?
    get() = childComponent.selectedItem?.let { it as ExistingPySdkComboBoxItem }?.sdk

  var selectedSdk: Sdk
    get() = selectedSdkIfExists!!
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
    class PySdkComboBoxTextAccessor(private val targetEnvironmentConfiguration: TargetEnvironmentConfiguration?) : TextComponentAccessor<JComboBox<PySdkComboBoxItem>> {
      override fun getText(component: JComboBox<PySdkComboBoxItem>): String =
        (component.selectedItem as? ExistingPySdkComboBoxItem)?.getText().orEmpty()

      override fun setText(component: JComboBox<PySdkComboBoxItem>, text: String) {
        val newItem = ExistingPySdkComboBoxItem(createDetectedSdk(text, targetEnvironmentConfiguration))
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
