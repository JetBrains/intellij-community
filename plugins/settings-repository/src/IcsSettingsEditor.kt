package org.jetbrains.settingsRepository

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.project.Project
import javax.swing.JComponent
import com.intellij.util.ui.FormBuilder
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
import java.awt.Component
import javax.swing.border.Border
import javax.swing.border.EmptyBorder
import com.intellij.util.ui.UIUtil
import com.intellij.ui.JBTabsPaneImpl
import javax.swing.SwingConstants
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsListener
import java.awt.Container
import javax.swing.Action
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.ArrayUtil
import javax.swing.AbstractAction
import java.awt.event.ActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import org.jetbrains.settingsRepository.actions.NOTIFICATION_GROUP
import com.intellij.notification.NotificationType
import java.awt.BorderLayout
import javax.swing.JPanel
import java.util.ArrayList

class IcsSettingsEditor(project: Project?) : DialogWrapper(project, true) {
  {
    setTitle(IcsBundle.message("settings.panel.title"))
    init()
  }

  override fun createCenterPanel(): JComponent? {
    val upstreamEditor = IcsSettingsPanel(null, getContentPane()!!, { doOKAction() })
    upstreamEditor.panel.setBorder(DialogWrapper.ourDefaultBorder)

    val tabbedPane = JBTabsPaneImpl(null, SwingConstants.TOP, myDisposable)
    val tabs = tabbedPane.getTabs()

    val upstreamTabInfo = TabInfo(wrap(upstreamEditor.panel, upstreamEditor.createActions())).setText("Upstream")
    val sourcesTabInfo = TabInfo(createReadOnlySourcesEditor(getContentPane())).setText("Read-only Sources")

    tabs.addTab(upstreamTabInfo)
    tabs.addTab(sourcesTabInfo)

    tabs.addListener(object: TabsListener.Adapter() {
      override fun beforeSelectionChanged(oldSelection: TabInfo?, newSelection: TabInfo?) {
        if (newSelection != null && oldSelection != null) {
          val oldTabSize = oldSelection.getComponent().getPreferredSize()
          val newTabSize = newSelection.getComponent().getPreferredSize()
          val dialogSize = getSize()
          setSize(dialogSize.width + (newTabSize.width - oldTabSize.width), dialogSize.height + (newTabSize.height - oldTabSize.height))
          repaint();
        }
      }
    })
    return tabbedPane.getComponent()
  }

  private fun wrap(component: JComponent, actions: Array<Action>): JComponent {
    val panel = JPanel(BorderLayout())
    panel.add(component, BorderLayout.CENTER)
    panel.add(createButtons(actions, ArrayList()), BorderLayout.SOUTH)
    return panel
  }

  override fun createSouthPanel() = null

  override protected fun createContentPaneBorder(): Border {
    val insets = UIUtil.PANEL_REGULAR_INSETS
    return EmptyBorder(insets.top, 0, insets.bottom, 0)
  }
}

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

private fun createReadOnlySourcesEditor(dialogParent:Component): JComponent {
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
  return editor.createComponent()
}

fun createDialogActions(project: Project?, urlTextField: TextFieldWithBrowseButton, dialogParent: Container, okAction: (() -> Unit)): Array<Action> {
  var syncTypes = SyncType.values()
  if (SystemInfo.isMac) {
    syncTypes = ArrayUtil.reverseArray(syncTypes)
  }

  val icsManager = IcsManager.getInstance()

  return Array(3) {
    val syncType = syncTypes[it]
    object : AbstractAction(IcsBundle.message("action." + (if (syncType == SyncType.MERGE) "Merge" else (if (syncType == SyncType.RESET_TO_THEIRS) "ResetToTheirs" else "ResetToMy")) + "Settings.text")) {
      fun saveRemoteRepositoryUrl(): Boolean {
        val url = StringUtil.nullize(urlTextField.getText())
        if (url != null && !icsManager.repositoryService.checkUrl(url, dialogParent)) {
          return false
        }

        val repositoryManager = icsManager.repositoryManager
        repositoryManager.createRepositoryIfNeed()
        repositoryManager.setUpstream(url, null)
        return true
      }

      override fun actionPerformed(event: ActionEvent) {
        val repositoryWillBeCreated = !icsManager.repositoryManager.isRepositoryExists()
        var upstreamSet = false
        try {
          if (!saveRemoteRepositoryUrl()) {
            if (repositoryWillBeCreated) {
              // remove created repository
              icsManager.repositoryManager.deleteRepository()
            }
            return
          }
          [suppress("UNUSED_VALUE")]
          (upstreamSet = true)

          if (repositoryWillBeCreated && syncType != SyncType.RESET_TO_THEIRS) {
            ApplicationManager.getApplication().saveSettings()

            icsManager.sync(syncType, project, { copyLocalConfig() })
          }
          else {
            icsManager.sync(syncType, project, null)
          }
        }
        catch (e: Throwable) {
          if (repositoryWillBeCreated) {
            // remove created repository
            icsManager.repositoryManager.deleteRepository()
          }

          LOG.warn(e)

          if (!upstreamSet || e is NoRemoteRepositoryException) {
            Messages.showErrorDialog(dialogParent, IcsBundle.message("set.upstream.failed.message", e.getMessage()), IcsBundle.message("set.upstream.failed.title"))
          }
          else {
            Messages.showErrorDialog(dialogParent, StringUtil.notNullize(e.getMessage(), "Internal error"), IcsBundle.message(if (e is AuthenticationException) "sync.not.authorized.title" else "sync.rejected.title"))
          }
          return
        }


        NOTIFICATION_GROUP.createNotification(IcsBundle.message("sync.done.message"), NotificationType.INFORMATION).notify(project)
        okAction()
      }
    }
  }
}