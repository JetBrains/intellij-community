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

import com.intellij.configurationStore.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.application.runBatchUpdate
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.options.ExternalizableScheme
import com.intellij.openapi.options.Scheme
import com.intellij.openapi.options.SchemesManagerFactory
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.util.SmartList
import com.intellij.util.messages.MessageBus
import com.intellij.util.ui.UIUtil
import gnu.trove.THashSet
import org.eclipse.jgit.errors.NoRemoteRepositoryException
import java.util.*

internal class SyncManager(private val icsManager: IcsManager, private val autoSyncManager: AutoSyncManager) {
  @Volatile var writeAndDeleteProhibited = false
    private set

  fun sync(syncType: SyncType, project: Project? = null, localRepositoryInitializer: (() -> Unit)? = null): UpdateResult? {
    var exception: Throwable? = null
    var restartApplication = false
    var updateResult: UpdateResult? = null
    icsManager.runInAutoCommitDisabledMode {
      UIUtil.invokeAndWaitIfNeeded(Runnable { ApplicationManager.getApplication()!!.saveSettings() })
      try {
        writeAndDeleteProhibited = true
        ProgressManager.getInstance().run(object : Task.Modal(project, icsMessage("task.sync.title"), true) {
          override fun run(indicator: ProgressIndicator) {
            indicator.isIndeterminate = true

            autoSyncManager.waitAutoSync(indicator)

            val repositoryManager = icsManager.repositoryManager
            if (localRepositoryInitializer == null) {
              try {
                // we commit before even if sync "OVERWRITE_LOCAL" — preserve history and ability to undo
                repositoryManager.commit(indicator, syncType)
                // well, we cannot commit? No problem, upcoming action must do something smart and solve the situation
              }
              catch (e: ProcessCanceledException) {
                LOG.warn("Canceled")
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

            if (indicator.isCanceled) {
              return
            }

            try {
              when (syncType) {
                SyncType.MERGE -> {
                  updateResult = repositoryManager.pull(indicator)
                  var doPush = true
                  if (localRepositoryInitializer != null) {
                    // must be performed only after initial pull, so, local changes will be relative to remote files
                    localRepositoryInitializer()
                    if (!repositoryManager.commit(indicator, syncType) || repositoryManager.getAheadCommitsCount() == 0) {
                      // avoid error during findRemoteRefUpdatesFor on push - if localRepositoryInitializer specified and nothing to commit (failed or just no files to commit (empty local configuration - no files)),
                      // so, nothing to push
                      doPush = false
                    }
                  }
                  if (doPush) {
                    repositoryManager.push(indicator)
                  }
                }
                SyncType.OVERWRITE_LOCAL -> {
                  // we don't push - probably, repository will be modified/removed (user can do something, like undo) before any other next push activities (so, we don't want to disturb remote)
                  updateResult = repositoryManager.resetToTheirs(indicator)
                }
                SyncType.OVERWRITE_REMOTE -> {
                  updateResult = repositoryManager.resetToMy(indicator, localRepositoryInitializer)
                  if (repositoryManager.getAheadCommitsCount() > 0) {
                    repositoryManager.push(indicator)
                  }
                }
              }
            }
            catch (e: ProcessCanceledException) {
              LOG.debug("Canceled")
              return
            }
            catch (e: Throwable) {
              if (e !is AuthenticationException && e !is NoRemoteRepositoryException && e !is CannotResolveConflictInTestMode) {
                LOG.error(e)
              }
              exception = e
              return
            }

            icsManager.repositoryActive = true
            val isSmartSchemeReload = syncType != SyncType.OVERWRITE_LOCAL
            if (updateResult != null) {
              val app = ApplicationManager.getApplication()
              restartApplication = updateStoragesFromStreamProvider(app.stateStore as ComponentStoreImpl, updateResult!!, app.messageBus, syncType == SyncType.OVERWRITE_LOCAL)
            }
            if (!restartApplication && !isSmartSchemeReload) {
              (SchemesManagerFactory.getInstance() as SchemeManagerFactoryBase).process {
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

internal fun updateStoragesFromStreamProvider(store: ComponentStoreImpl, updateResult: UpdateResult, messageBus: MessageBus, reloadAllSchemes: Boolean = false): Boolean {
  val changedComponentNames = LinkedHashSet<String>()
  val (changed, deleted) = (store.storageManager as StateStorageManagerImpl).getCachedFileStorages(updateResult.changed, updateResult.deleted, { toIdeaPath(it) })

  val schemeManagersToReload = SmartList<SchemeManagerImpl<Scheme, ExternalizableScheme>>()
  (SchemesManagerFactory.getInstance() as SchemeManagerFactoryBase).process {
    if (reloadAllSchemes) {
      schemeManagersToReload.add(it)
    }
    else {
      for (path in updateResult.changed) {
        if (it.fileSpec == toIdeaPath(path)) {
          schemeManagersToReload.add(it)
        }
      }
      for (path in updateResult.deleted) {
        if (it.fileSpec == toIdeaPath(path)) {
          schemeManagersToReload.add(it)
        }
      }
    }
  }

  if (changed.isEmpty() && deleted.isEmpty() && schemeManagersToReload.isEmpty()) {
    return false
  }

  return UIUtil.invokeAndWaitIfNeeded(Computable<kotlin.Boolean> {
    val notReloadableComponents: Collection<String>
    updateStateStorage(changedComponentNames, changed, false)
    updateStateStorage(changedComponentNames, deleted, true)

    for (schemeManager in schemeManagersToReload) {
      schemeManager.reload()
    }

    if (changedComponentNames.isEmpty()) {
      return@Computable false
    }

    notReloadableComponents = store.getNotReloadableComponents(changedComponentNames)

    val changedStorageSet = THashSet<StateStorage>(changed)
    changedStorageSet.addAll(deleted)
    runBatchUpdate(messageBus) {
      store.reinitComponents(changedComponentNames, changedStorageSet, notReloadableComponents)
    }

    !notReloadableComponents.isEmpty() && askToRestart(store, notReloadableComponents, null, true)
  })!!
}

private fun updateStateStorage(changedComponentNames: MutableSet<String>, stateStorages: Collection<StateStorage>, deleted: Boolean) {
  for (stateStorage in stateStorages) {
    try {
      (stateStorage as XmlElementStorage).updatedFromStreamProvider(changedComponentNames, deleted)
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

class NoRemoteRepositoryException(cause: Throwable) : RuntimeException(cause.message, cause)

class CannotResolveConflictInTestMode() : RuntimeException()