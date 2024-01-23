// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.settingsRepository

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.Function
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.delete
import com.intellij.util.text.nullize
import com.intellij.util.text.trimMiddle
import com.intellij.util.ui.table.TableModelEditor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import org.jetbrains.settingsRepository.git.JGitCoroutineProgressMonitor
import org.jetbrains.settingsRepository.git.cloneBare
import kotlin.io.path.exists
import kotlin.properties.Delegates.notNull

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
  val itemEditor = object : TableModelEditor.DialogItemEditor<ReadonlySource> {
    override fun clone(item: ReadonlySource, forInPlaceEditing: Boolean) = ReadonlySource(item.url, item.active)

    override fun getItemClass() = ReadonlySource::class.java

    override fun edit(item: ReadonlySource, mutator: Function<in ReadonlySource, out ReadonlySource>, isAdd: Boolean) {
      var urlField: TextFieldWithBrowseButton by notNull()
      val panel = panel {
        row(IcsBundle.message("readonly.sources.configuration.url.label")) {
          urlField = textFieldWithBrowseButton(IcsBundle.message("readonly.sources.configuration.repository.chooser"),
                                               fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor())
            .columns(COLUMNS_LARGE)
            .component
        }
      }

      dialog(title = IcsBundle.message("readonly.sources.configuration.add.source"), panel = panel, focusedComponent = urlField) {
        val url = urlField.text.nullize(true)
        validateUrl(url, null)?.let {
          return@dialog listOf(ValidationInfo(it))
        }

        mutator.`fun`(item).url = url
        return@dialog null
      }
        .show()
    }

    override fun applyEdited(oldItem: ReadonlySource, newItem: ReadonlySource) {
      newItem.url = oldItem.url
    }

    override fun isUseDialogToAdd() = true
  }

  val editor = TableModelEditor(COLUMNS, itemEditor, IcsBundle.message("readonly.sources.configuration.no.sources.configured"))
  editor.reset(if (ApplicationManager.getApplication().isUnitTestMode) emptyList() else icsManager.settings.readOnlySources)
  return object : ConfigurableUi<IcsSettings> {
    override fun isModified(settings: IcsSettings) = editor.isModified

    override fun apply(settings: IcsSettings) {
      val oldList = settings.readOnlySources
      val toDelete = CollectionFactory.createSmallMemoryFootprintSet<String>(oldList.size)
      for (oldSource in oldList) {
        ContainerUtil.addIfNotNull(toDelete, oldSource.path)
      }

      val toCheckout = CollectionFactory.createSmallMemoryFootprintSet<ReadonlySource>()

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

      runWithModalProgressBlocking(ModalTaskOwner.guess(), icsMessage("task.sync.title")) {
        reportRawProgress { reporter ->
          val root = icsManager.readOnlySourcesManager.rootDir

          if (toDelete.isNotEmpty()) {
            reporter.text(icsMessage("progress.deleting.old.repositories"))
            for (path in toDelete) {
              ensureActive()
              LOG.runAndLogException {
                reporter.details(path)
                root.resolve(path).delete()
              }
            }
          }

          if (toCheckout.isNotEmpty()) {
            for (source in toCheckout) {
              ensureActive()
              LOG.runAndLogException {
                reporter.text(icsMessage("progress.cloning.repository", source.url!!.trimMiddle(255)))
                val dir = root.resolve(source.path!!)
                if (dir.exists()) {
                  dir.delete()
                }
                val progressMonitor = JGitCoroutineProgressMonitor(currentCoroutineContext().job, reporter)
                blockingContext {
                  cloneBare(source.url!!, dir, icsManager.credentialsStore, progressMonitor).close()
                }
              }
            }
          }

          icsManager.readOnlySourcesManager.setSources(newList)

          // blindly reload all
          icsManager.schemeManagerFactory.value.process {
            it.reload()
          }
        }
      }
    }

    override fun reset(settings: IcsSettings) {
      editor.reset(settings.readOnlySources)
    }

    override fun getComponent() = editor.createComponent()
  }
}
