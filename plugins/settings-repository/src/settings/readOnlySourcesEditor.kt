package org.jetbrains.settingsRepository

import java.awt.Component
import com.intellij.util.ui.table.TableModelEditor
import com.intellij.util.Function
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import javax.swing.JTextField
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.ui.DocumentAdapter
import javax.swing.event.DocumentEvent
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ui.FormBuilder

private val COLUMNS = array(
    object : TableModelEditor.EditableColumnInfo<ReadonlySource, Boolean>() {
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

private fun createReadOnlySourcesEditor(dialogParent:Component): Configurable {
  val itemEditor = object : TableModelEditor.DialogItemEditor<ReadonlySource>() {
    override fun clone(item: ReadonlySource, forInPlaceEditing: Boolean) = ReadonlySource(item.active, item.url)

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
            enabled = url != null && url.length() > 1 && IcsManager.getInstance().repositoryService.checkUrl(url, null)
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
  editor.reset(IcsManager.getInstance().settings.readOnlySources)
  return object : Configurable {
    override fun isModified() = editor.isModified(IcsManager.getInstance().settings.readOnlySources)

    override fun apply() {
      IcsManager.getInstance().settings.readOnlySources = editor.apply()
    }

    override fun reset() {
      editor.reset(IcsManager.getInstance().settings.readOnlySources)
    }

    override fun getComponent() = editor.createComponent()
  }
}
