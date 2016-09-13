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

import com.intellij.configurationStore.ComponentStoreImpl
import com.intellij.configurationStore.SchemeManagerFactoryBase
import com.intellij.configurationStore.StateStorageManagerImpl
import com.intellij.configurationStore.reloadAppStore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.options.ConfigurableUi
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

internal fun createRepositoryListEditor(): ConfigurableUi<IcsSettings> {
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

  return object: ConfigurableUi<IcsSettings> {
    override fun isModified(settings: IcsSettings) = editor.isModified

    override fun getComponent() = panel {
      row("Repository:") {
        editor.comboBox()
        deleteButton()
      }
    }

    override fun apply(settings: IcsSettings) {
      val newList = editor.apply()
      if (newList.isEmpty()) {
        // repo is deleted
        deleteRepository()
      }
    }

    override fun reset(settings: IcsSettings) {
      val list = ArrayList<RepositoryItem>()
      val upstream = icsManager.repositoryManager.getUpstream()?.let { RepositoryItem(it) }
      upstream?.let {
        list.add(it)
      }

      editor.reset(list)
      editor.model.selectedItem = upstream

      deleteButton.isEnabled = editor.model.selectedItem != null
    }
  }
}

private fun deleteRepository() {
  // as two tasks, - user should be able to cancel syncing before delete and continue to delete
  runModalTask("Syncing before delete Repository", cancellable = true) { indicator ->
    indicator.isIndeterminate = true

    val repositoryManager = icsManager.repositoryManager

    // attempt to fetch, merge and push to ensure that latest changes in the deleted user repository will be not lost
    // yes, — delete repository doesn't mean "AAA, delete it, delete". It means just that user doesn't need it at this moment.
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