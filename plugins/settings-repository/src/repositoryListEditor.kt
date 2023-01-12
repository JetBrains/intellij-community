// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.settingsRepository

import com.intellij.configurationStore.ComponentStoreImpl
import com.intellij.configurationStore.StateStorageManagerImpl
import com.intellij.configurationStore.reloadAppStore
import com.intellij.configurationStore.schemeManager.SchemeManagerFactoryBase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.progress.ModalTaskOwner
import com.intellij.openapi.progress.runBlockingModal
import com.intellij.openapi.progress.runModalTask
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.ComboBoxModelEditor
import com.intellij.util.ui.ListItemEditor
import kotlinx.coroutines.ensureActive
import javax.swing.JButton

private class RepositoryItem(var url: String? = null) {
  override fun toString() = url ?: ""
}

internal fun createRepositoryListEditor(icsManager: IcsManager): DialogPanel {
  val editor = ComboBoxModelEditor(object : ListItemEditor<RepositoryItem> {
    override fun getItemClass() = RepositoryItem::class.java

    override fun clone(item: RepositoryItem, forInPlaceEditing: Boolean) = RepositoryItem(item.url)
  })

  lateinit var deleteButton: Cell<JButton>

  return panel {
    row(icsMessage("repository.editor.repository.label")) {
      cell(editor.comboBox)
        .comment(icsMessage("repository.editor.combobox.comment"))
      deleteButton = button(IcsBundle.message("repository.editor.delete.button")) {
        editor.model.selected?.let { selected ->
          editor.model.remove(selected)
          deleteButton.enabled(editor.model.selected != null)
        }
      }
    }

    onIsModified { editor.isModified }
    onApply {
      val newList = editor.apply()
      if (newList.isEmpty()) {
        // repo is deleted
        deleteRepository(icsManager)
      }
    }
    onReset {
      val list = ArrayList<RepositoryItem>()
      val upstream = icsManager.repositoryManager.getUpstream()?.let(::RepositoryItem)
      upstream?.let {
        list.add(it)
      }

      editor.reset(list)
      editor.model.selectedItem = upstream

      deleteButton.enabled(editor.model.selectedItem != null)
    }
  }
}

private fun deleteRepository(icsManager: IcsManager) {
  // as two tasks, - user should be able to cancel syncing before delete and continue to delete
  runBlockingModal(ModalTaskOwner.guess(), IcsBundle.message("progress.syncing.before.deleting.repository")) {
    val repositoryManager = icsManager.repositoryManager

    // attempt to fetch, merge and push to ensure that latest changes in the deleted user repository will be not lost
    // yes, - delete repository doesn't mean "AAA, delete it, delete". It means just that user doesn't need it at this moment.
    // It is user responsibility later to delete git repository or do whatever user want. Our responsibility is to not loose user changes.
    if (!repositoryManager.canCommit()) {
      LOG.info("Commit on repository delete skipped: repository is not committable")
      return@runBlockingModal
    }

    catchAndLog(asWarning = true) {
      val updater = repositoryManager.fetch()
      ensureActive()
      // ignore result, we don't need to apply it
      updater.merge()
      ensureActive()
      if (!updater.definitelySkipPush) {
        repositoryManager.push()
      }
    }
  }

  runModalTask(IcsBundle.message("progress.deleting.repository"), cancellable = false) { indicator ->
    val repositoryManager = icsManager.repositoryManager

    indicator.isIndeterminate = true

    try {
      repositoryManager.deleteRepository()
    }
    finally {
      icsManager.isRepositoryActive = false
    }
  }

  val store = ApplicationManager.getApplication().stateStore as ComponentStoreImpl
  if (!reloadAppStore((store.storageManager as StateStorageManagerImpl).getCachedFileStorages())) {
    return
  }

  (SchemeManagerFactory.getInstance() as SchemeManagerFactoryBase).process {
    it.reload()
  }
}