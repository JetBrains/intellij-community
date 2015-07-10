/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.configurationStore.SchemeManagerFactoryImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.impl.stores.ComponentStoreImpl
import com.intellij.openapi.components.impl.stores.FileBasedStorage
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.options.SchemesManagerFactory
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.util.ui.UIUtil
import gnu.trove.THashSet
import java.util.LinkedHashSet

class SyncManager(private val icsManager: IcsManager, private val autoSyncManager: AutoSyncManager) {
  volatile var writeAndDeleteProhibited = false
    private set

  public fun sync(syncType: SyncType, project: Project?, localRepositoryInitializer: (() -> Unit)? = null): UpdateResult? {
    ApplicationManager.getApplication()!!.assertIsDispatchThread()

    var exception: Throwable? = null
    var restartApplication = false
    var updateResult: UpdateResult? = null
    icsManager.runInAutoCommitDisabledMode {
      ApplicationManager.getApplication()!!.saveSettings()
      try {
        writeAndDeleteProhibited = true
        ProgressManager.getInstance().run(object : Task.Modal(project, IcsBundle.message("task.sync.title"), true) {
          override fun run(indicator: ProgressIndicator) {
            indicator.setIndeterminate(true)

            autoSyncManager.waitAutoSync(indicator)

            val repositoryManager = icsManager.repositoryManager
            if (localRepositoryInitializer == null) {
              try {
                // we commit before even if sync "RESET_TO_THEIRS" — preserve history and ability to undo
                repositoryManager.commitIfCan(indicator)
                // well, we cannot commit? No problem, upcoming action must do something smart and solve the situation
              }
              catch (e: ProcessCanceledException) {
                LOG.debug("Canceled")
                return
              }
              catch (e: Throwable) {
                LOG.error(e)

                // "RESET_TO_*" will do "reset hard", so, probably, error will be gone, so, we can continue operation
                if (syncType == SyncType.MERGE) {
                  exception = e
                  return
                }
              }
            }

            if (indicator.isCanceled()) {
              return
            }

            try {
              when (syncType) {
                SyncType.MERGE -> {
                  updateResult = repositoryManager.pull(indicator)
                  if (localRepositoryInitializer != null) {
                    // must be performed only after initial pull, so, local changes will be relative to remote files
                    localRepositoryInitializer()
                    repositoryManager.commit(indicator)
                    updateResult = updateResult.concat(repositoryManager.pull(indicator))
                  }
                  repositoryManager.push(indicator)
                }
                SyncType.OVERWRITE_LOCAL -> {
                  // we don't push - probably, repository will be modified/removed (user can do something, like undo) before any other next push activities (so, we don't want to disturb remote)
                  updateResult = repositoryManager.resetToTheirs(indicator)
                }
                SyncType.OVERWRITE_REMOTE -> {
                  updateResult = repositoryManager.resetToMy(indicator, localRepositoryInitializer)
                  repositoryManager.push(indicator)
                }
              }
            }
            catch (e: ProcessCanceledException) {
              LOG.debug("Canceled")
              return
            }
            catch (e: Throwable) {
              if (e !is AuthenticationException && e !is NoRemoteRepositoryException) {
                LOG.error(e)
              }
              exception = e
              return
            }

            icsManager.repositoryActive = true
            if (updateResult != null) {
              restartApplication = updateStoragesFromStreamProvider((ApplicationManager.getApplication() as ApplicationImpl).getStateStore(), updateResult!!)
            }
            if (!restartApplication && syncType == SyncType.OVERWRITE_LOCAL) {
              (SchemesManagerFactory.getInstance() as SchemeManagerFactoryImpl).process {
                it.reload()
              }
            }
          }
        })
      }
      finally {
        writeAndDeleteProhibited = false
      }
    }

    if (restartApplication) {
      // force to avoid saveAll & confirmation
      (ApplicationManager.getApplication() as ApplicationImpl).exit(true, true, true, true)
    }
    else if (exception != null) {
      throw exception!!
    }
    return updateResult
  }
}

private fun updateStoragesFromStreamProvider(store: IComponentStore.Reloadable, updateResult: UpdateResult): Boolean {
  val changedComponentNames = LinkedHashSet<String>()
  val stateStorages = store.getStateStorageManager().getCachedFileStateStorages(updateResult.changed, updateResult.deleted)
  val changed = stateStorages.first!!
  val deleted = stateStorages.second!!
  if (changed.isEmpty() && deleted.isEmpty()) {
    return false
  }

  return UIUtil.invokeAndWaitIfNeeded(object : Computable<Boolean> {
    override fun compute(): Boolean {
      val notReloadableComponents: Collection<String>
      val token = WriteAction.start()
      try {
        updateStateStorage(changedComponentNames, changed, false)
        updateStateStorage(changedComponentNames, deleted, true)

        if (changedComponentNames.isEmpty()) {
          return false
        }

        notReloadableComponents = store.getNotReloadableComponents(changedComponentNames)

        val changedStorageSet = THashSet(changed)
        changedStorageSet.addAll(deleted)
        (store as ComponentStoreImpl).reinitComponents(changedComponentNames, notReloadableComponents, changedStorageSet)
      }
      finally {
        token.finish()
      }

      if (notReloadableComponents.isEmpty()) {
        return false
      }
      return ComponentStoreImpl.askToRestart(store, notReloadableComponents, null)
    }
  })!!
}

private fun updateStateStorage(changedComponentNames: Set<String>, stateStorages: Collection<FileBasedStorage>, deleted: Boolean) {
  for (stateStorage in stateStorages) {
    try {
      stateStorage.updatedFromStreamProvider(changedComponentNames, deleted)
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }
}

enum class SyncType {
  MERGE,
  OVERWRITE_LOCAL,
  OVERWRITE_REMOTE
}