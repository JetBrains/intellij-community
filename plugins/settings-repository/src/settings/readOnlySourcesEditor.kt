package org.jetbrains.settingsRepository

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.DocumentAdapter
import com.intellij.util.Function
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.table.TableModelEditor
import gnu.trove.THashSet
import org.jetbrains.settingsRepository.git.asProgressMonitor
import org.jetbrains.settingsRepository.git.cloneBare
import java.awt.Component
import java.io.File
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

private val COLUMNS = arrayOf(object : TableModelEditor.EditableColumnInfo<ReadonlySource, Boolean>() {
  override fun getColumnClass() = javaClass<Boolean>()

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

private fun createReadOnlySourcesEditor(dialogParent: Component, project: Project?): Configurable {
  val itemEditor = object : TableModelEditor.DialogItemEditor<ReadonlySource>() {
    override fun clone(item: ReadonlySource, forInPlaceEditing: Boolean) = ReadonlySource(item.url, item.active)

    override fun getItemClass() = javaClass<ReadonlySource>()

    override fun edit(item: ReadonlySource, mutator: Function<ReadonlySource, ReadonlySource>, isAdd: Boolean) {
      val dialogBuilder = DialogBuilder(dialogParent)
      val urlField = TextFieldWithBrowseButton(JTextField(20))
      urlField.addBrowseFolderListener(TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFolderDescriptor()))
      urlField.getTextField().getDocument().addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(event: DocumentEvent) {
          val url = StringUtil.nullize(urlField.getText())
          val enabled: Boolean
          try {
            enabled = url != null && url.length() > 1 && icsManager.repositoryService.checkUrl(url, null)
          }
          catch (e: Exception) {
            enabled = false
          }

          dialogBuilder.setOkActionEnabled(enabled)
        }
      })

      dialogBuilder.title("Add read-only source").resizable(false).centerPanel(FormBuilder.createFormBuilder().addLabeledComponent("URL:", urlField).getPanel()).setPreferredFocusComponent(urlField)
      if (dialogBuilder.showAndGet()) {
        mutator.`fun`(item).url = urlField.getText()
      }
    }

    override fun applyEdited(oldItem: ReadonlySource, newItem: ReadonlySource) {
      newItem.url = oldItem.url
    }

    override fun isUseDialogToAdd() = true
  }

  val editor = TableModelEditor(COLUMNS, itemEditor, "No sources configured")
  editor.reset(icsManager.settings.readOnlySources)
  return object : Configurable {
    override fun isModified() = editor.isModified()

    override fun apply() {
      val oldList = icsManager.settings.readOnlySources
      val toDelete = THashSet<String>(oldList.size())
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

      if (toDelete.isEmpty() && toCheckout.isEmpty()) {
        return
      }

      ProgressManager.getInstance().run(object : Task.Modal(project, IcsBundle.message("task.sync.title"), true) {
        override fun run(indicator: ProgressIndicator) {
          indicator.setIndeterminate(true)

          val root = getPluginSystemDir()

          if (toDelete.isNotEmpty()) {
            indicator.setText("Deleting old repositories")
            for (path in toDelete) {
              indicator.checkCanceled()
              try {
                indicator.setText2(path)
                FileUtil.delete(File(root, path))
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
                indicator.setText("Cloning ${StringUtil.trimMiddle(source.url!!, 255)}")
                cloneBare(source.url!!, File(root, source.path!!), credentialsStore, indicator.asProgressMonitor()).close()
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

    override fun reset() {
      editor.reset(icsManager.settings.readOnlySources)
    }

    override fun getComponent() = editor.createComponent()
  }
}
