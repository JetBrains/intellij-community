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

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.DocumentAdapter
import com.intellij.util.Function
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.deleteRecursively
import com.intellij.util.exists
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.table.TableModelEditor
import gnu.trove.THashSet
import org.jetbrains.settingsRepository.git.asProgressMonitor
import org.jetbrains.settingsRepository.git.cloneBare
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

private val COLUMNS = arrayOf(object : TableModelEditor.EditableColumnInfo<ReadonlySource, Boolean>() {
  override fun getColumnClass() = Boolean::class.java

  override fun valueOf(item: ReadonlySource) = item.active

  override fun setValue(item: ReadonlySource, value: Boolean) {
    item.active = value
  }
},
    object : TableModelEditor.EditableColumnInfo<ReadonlySource, String>() {
      override fun valueOf(item: ReadonlySource) = item.url

      override fun setValue(item: ReadonlySource, value: String) {
        item.url = value
      }
    })

internal fun createReadOnlySourcesEditor(): ConfigurableUi<IcsSettings> {
  val itemEditor = object : TableModelEditor.DialogItemEditor<ReadonlySource>() {
    override fun clone(item: ReadonlySource, forInPlaceEditing: Boolean) = ReadonlySource(item.url, item.active)

    override fun getItemClass() = ReadonlySource::class.java

    override fun edit(item: ReadonlySource, mutator: Function<ReadonlySource, ReadonlySource>, isAdd: Boolean) {
      val dialogBuilder = DialogBuilder()
      val urlField = TextFieldWithBrowseButton(JTextField(20))
      urlField.addBrowseFolderListener(TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFolderDescriptor()))
      urlField.textField.document.addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(event: DocumentEvent) {
          val url = StringUtil.nullize(urlField.text)
          val enabled: Boolean
          try {
            enabled = url != null && url.length > 1 && icsManager.repositoryService.checkUrl(url, null)
          }
          catch (e: Exception) {
            enabled = false
          }

          dialogBuilder.setOkActionEnabled(enabled)
        }
      })

      dialogBuilder.title("Add read-only source").resizable(false).centerPanel(FormBuilder.createFormBuilder().addLabeledComponent("URL:", urlField).panel).setPreferredFocusComponent(urlField)
      if (dialogBuilder.showAndGet()) {
        mutator.`fun`(item).url = urlField.text
      }
    }

    override fun applyEdited(oldItem: ReadonlySource, newItem: ReadonlySource) {
      newItem.url = oldItem.url
    }

    override fun isUseDialogToAdd() = true
  }

  val editor = TableModelEditor(COLUMNS, itemEditor, "No sources configured")
  editor.reset(icsManager.settings.readOnlySources)
  return object : ConfigurableUi<IcsSettings> {
    override fun isModified(settings: IcsSettings) = editor.isModified

    override fun apply(settings: IcsSettings) {
      val oldList = settings.readOnlySources
      val toDelete = THashSet<String>(oldList.size)
      for (oldSource in oldList) {
        ContainerUtil.addIfNotNull(toDelete, oldSource.path)
      }

      val toCheckout = THashSet<ReadonlySource>()

      val newList = editor.apply()
      for (newSource in newList) {
        val path = newSource.path
        if (path != null && !toDelete.remove(path)) {
          toCheckout.add(newSource)
        }
      }

      if (toDelete.isEmpty && toCheckout.isEmpty) {
        return
      }

      ProgressManager.getInstance().run(object : Task.Modal(null, icsMessage("task.sync.title"), true) {
        override fun run(indicator: ProgressIndicator) {
          indicator.isIndeterminate = true

          val root = icsManager.readOnlySourcesManager.rootDir

          if (toDelete.isNotEmpty()) {
            indicator.text = "Deleting old repositories"
            for (path in toDelete) {
              indicator.checkCanceled()
              try {
                indicator.text2 = path
                root.resolve(path).deleteRecursively()
              }
              catch (e: Exception) {
                LOG.error(e)
              }
            }
          }

          if (toCheckout.isNotEmpty()) {
            for (source in toCheckout) {
              indicator.checkCanceled()
              try {
                indicator.text = "Cloning ${StringUtil.trimMiddle(source.url!!, 255)}"
                val dir = root.resolve(source.path!!)
                if (dir.exists()) {
                  dir.deleteRecursively()
                }
                cloneBare(source.url!!, dir, icsManager.credentialsStore, indicator.asProgressMonitor()).close()
              }
              catch (e: Exception) {
                LOG.error(e)
              }
            }
          }

          icsManager.readOnlySourcesManager.setSources(newList)
        }
      })
    }

    override fun reset(settings: IcsSettings) {
      editor.reset(settings.readOnlySources)
    }

    override fun getComponent() = editor.createComponent()
  }
}
