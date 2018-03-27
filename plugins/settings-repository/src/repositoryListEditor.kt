// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.settingsRepository

import com.intellij.configurationStore.ComponentStoreImpl
import com.intellij.configurationStore.SchemeManagerFactoryBase
import com.intellij.configurationStore.StateStorageManagerImpl
import com.intellij.configurationStore.reloadAppStore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.progress.runModalTask
import com.intellij.ui.layout.*
import com.intellij.util.ui.ComboBoxModelEditor
import com.intellij.util.ui.ListItemEditor
import java.util.*
import javax.swing.JButton

internal class RepositoryItem(var url: String? = null) {
  override fun toString() = url ?: ""
}

internal fun createRepositoryListEditor(icsManager: IcsManager): ConfigurableUiEx<IcsSettings> {
  val editor = ComboBoxModelEditor(object: ListItemEditor<RepositoryItem> {
    override fun getItemClass() = RepositoryItem::class.java

    override fun clone(item: RepositoryItem, forInPlaceEditing: Boolean) = RepositoryItem(item.url)
  })

  val deleteButton = JButton("Delete")
  deleteButton.addActionListener {
    editor.model.selected?.let {
      editor.model.remove(it)
      deleteButton.isEnabled = editor.model.selected != null
    }
  }

  return object: ConfigurableUiEx<IcsSettings> {
    private var repositoryRow: Row? = null

    override fun isModified(settings: IcsSettings) = editor.isModified

    override fun buildUi(builder: LayoutBuilder) {
      builder.apply {
        repositoryRow = row("Repository:") {
          cell {
            editor.comboBox(comment = "Use File -> Settings Repository... to configure")
            deleteButton()
          }
        }
      }
    }

    override fun getComponent() = panel(init = ::buildUi)

    override fun apply(settings: IcsSettings) {
      val newList = editor.apply()
      if (newList.isEmpty()) {
        // repo is deleted
        deleteRepository(icsManager)
      }
    }

    override fun reset(settings: IcsSettings) {
      val list = ArrayList<RepositoryItem>()
      val upstream = icsManager.repositoryManager.getUpstream()?.let(::RepositoryItem)
      upstream?.let {
        list.add(it)
      }

      editor.reset(list)
      editor.model.selectedItem = upstream

      deleteButton.isEnabled = editor.model.selectedItem != null
    }
  }
}

private fun deleteRepository(icsManager: IcsManager) {
  // as two tasks, - user should be able to cancel syncing before delete and continue to delete
  runModalTask("Syncing before delete Repository", cancellable = true) { indicator ->
    indicator.isIndeterminate = true

    val repositoryManager = icsManager.repositoryManager

    // attempt to fetch, merge and push to ensure that latest changes in the deleted user repository will be not lost
    // yes, - delete repository doesn't mean "AAA, delete it, delete". It means just that user doesn't need it at this moment.
    // It is user responsibility later to delete git repository or do whatever user want. Our responsibility is to not loose user changes.
    if (!repositoryManager.canCommit()) {
      LOG.info("Commit on repository delete skipped: repository is not committable")
      return@runModalTask
    }

    catchAndLog(asWarning = true) {
      val updater = repositoryManager.fetch(indicator)
      indicator.checkCanceled()
      // ignore result, we don't need to apply it
      updater.merge()
      indicator.checkCanceled()
      if (!updater.definitelySkipPush) {
        repositoryManager.push(indicator)
      }
    }
  }

  runModalTask("Deleting Repository", cancellable = false) { indicator ->
    val repositoryManager = icsManager.repositoryManager

    indicator.isIndeterminate = true

    repositoryManager.deleteRepository()
    icsManager.repositoryActive = false
  }

  val store = ApplicationManager.getApplication().stateStore as ComponentStoreImpl
  if (!reloadAppStore((store.storageManager as StateStorageManagerImpl).getCachedFileStorages())) {
    return
  }

  (SchemeManagerFactory.getInstance() as SchemeManagerFactoryBase).process {
    it.reload()
  }
}