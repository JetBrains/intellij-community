// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.settingsRepository

import com.intellij.configurationStore.*
import com.intellij.configurationStore.schemeManager.SchemeManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.stateStore
import com.intellij.util.SmartList
import com.intellij.util.containers.CollectionFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.eclipse.jgit.errors.NoRemoteRepositoryException
import org.jetbrains.annotations.PropertyKey
import kotlin.coroutines.coroutineContext

internal class SyncManager(private val icsManager: IcsManager, private val autoSyncManager: AutoSyncManager) {
  @Volatile var writeAndDeleteProhibited = false
    private set

  private suspend fun runSyncTask(onAppExit: Boolean, task: suspend () -> Unit) {
    icsManager.runInAutoCommitDisabledMode {
      if (!onAppExit) {
        runInAutoSaveDisabledMode {
          saveSettings(ApplicationManager.getApplication(), false)
        }
      }

      try {
        writeAndDeleteProhibited = true
        task()
      }
      finally {
        writeAndDeleteProhibited = false
      }
    }
  }

  suspend fun sync(syncType: SyncType, localRepositoryInitializer: (() -> Unit)? = null, onAppExit: Boolean = false): Boolean {
    var exception: Throwable? = null
    var restartApplication = false
    var updateResult: UpdateResult? = null
    var isReadOnlySourcesChanged = false
    runSyncTask(onAppExit) {
      if (!onAppExit) {
        autoSyncManager.waitAutoSync()
      }

      val repositoryManager = icsManager.repositoryManager

      suspend fun updateRepository() {
        when (syncType) {
          SyncType.MERGE -> {
            updateResult = repositoryManager.pull()
            var doPush = true
            if (localRepositoryInitializer != null) {
              // must be performed only after initial pull, so, local changes will be relative to remote files
              localRepositoryInitializer()
              if (!repositoryManager.commit(syncType) || repositoryManager.getAheadCommitsCount() == 0) {
                // avoid error during findRemoteRefUpdatesFor on push - if localRepositoryInitializer specified and nothing to commit (failed or just no files to commit (empty local configuration - no files)),
                // so, nothing to push
                doPush = false
              }
            }
            if (doPush) {
              repositoryManager.push()
            }
          }
          SyncType.OVERWRITE_LOCAL -> {
            // we don't push - probably, repository will be modified/removed (user can do something, like undo) before any other next push activities (so, we don't want to disturb remote)
            updateResult = repositoryManager.resetToTheirs()
          }
          SyncType.OVERWRITE_REMOTE -> {
            updateResult = repositoryManager.resetToMy(localRepositoryInitializer)
            if (repositoryManager.getAheadCommitsCount() > 0) {
              repositoryManager.push()
            }
          }
        }
      }

      if (localRepositoryInitializer == null) {
        try {
          // we commit before even if sync "OVERWRITE_LOCAL" - preserve history and ability to undo
          repositoryManager.commit(syncType)
          // well, we cannot commit? No problem, upcoming action must do something smart and solve the situation
        }
        catch (e: CancellationException) {
          LOG.warn("Canceled")
          throw e
        }
        catch (e: Throwable) {
          LOG.error(e)

          // "RESET_TO_*" will do "reset hard", so, probably, error will be gone, so, we can continue operation
          if (syncType == SyncType.MERGE) {
            exception = e
            return@runSyncTask
          }
        }
      }

      coroutineContext.ensureActive()

      try {
        if (repositoryManager.hasUpstream()) {
          updateRepository()
        }

        isReadOnlySourcesChanged = updateCloudSchemes(icsManager)
      }
      catch (e: CancellationException) {
        LOG.debug("Canceled")
        throw e
      }
      catch (e: Throwable) {
        if (e !is AuthenticationException && e !is NoRemoteRepositoryException && e !is CannotResolveConflictInTestMode) {
          LOG.error(e)
        }
        exception = e
        return@runSyncTask
      }

      if (updateResult != null) {
        val app = ApplicationManager.getApplication()
        restartApplication = updateStoragesFromStreamProvider(icsManager = icsManager,
                                                              store = app.stateStore as ComponentStoreImpl,
                                                              updateResult = updateResult!!,
                                                              reloadAllSchemes = syncType == SyncType.OVERWRITE_LOCAL)

      }
    }

    if (!onAppExit && restartApplication) {
      // disable auto sync on exit
      autoSyncManager.enabled = false
      // force to avoid saveAll & confirmation
      ApplicationManager.getApplication().exit(true, true, true)
    }
    else if (exception != null) {
      throw exception!!
    }
    return updateResult != null || isReadOnlySourcesChanged
  }
}

internal suspend fun updateCloudSchemes(icsManager: IcsManager): Boolean {
  val changedRootDirs = icsManager.readOnlySourcesManager.update() ?: return false
  val schemeManagersToReload = SmartList<SchemeManagerImpl<*, *>>()
  icsManager.schemeManagerFactory.value.process {
    val fileSpec = toRepositoryPath(it.fileSpec, it.roamingType)
    if (changedRootDirs.contains(fileSpec)) {
      schemeManagersToReload.add(it)
    }
  }

  if (schemeManagersToReload.isNotEmpty()) {
    for (schemeManager in schemeManagersToReload) {
      schemeManager.reload()
    }
  }

  return schemeManagersToReload.isNotEmpty()
}


internal suspend fun updateStoragesFromStreamProvider(icsManager: IcsManager,
                                                      store: ComponentStoreImpl,
                                                      updateResult: UpdateResult,
                                                      reloadAllSchemes: Boolean = false): Boolean {
  val (changed, deleted) = (store.storageManager as StateStorageManagerImpl).getCachedFileStorages(changed = updateResult.changed,
                                                                                                   deleted = updateResult.deleted,
                                                                                                   pathNormalizer = ::toIdeaPath)

  val schemeManagersToReload = SmartList<SchemeManagerImpl<*, *>>()
  icsManager.schemeManagerFactory.value.process {
    if (reloadAllSchemes || shouldReloadSchemeManager(it, updateResult.changed.plus(updateResult.deleted))) {
      schemeManagersToReload.add(it)
    }
  }

  if (changed.isEmpty() && deleted.isEmpty() && schemeManagersToReload.isEmpty()) {
    return false
  }

  return withContext(Dispatchers.EDT) {
    val changedComponentNames = LinkedHashSet<String>()
    updateStateStorage(changedComponentNames, changed, false)
    updateStateStorage(changedComponentNames, deleted, true)

    for (schemeManager in schemeManagersToReload) {
      schemeManager.reload()
    }

    if (changedComponentNames.isEmpty()) {
      return@withContext false
    }

    val notReloadableComponents = store.getNotReloadableComponents(changedComponentNames)
    val changedStorageSet = CollectionFactory.createSmallMemoryFootprintSet<StateStorage>(changed)
    changedStorageSet.addAll(deleted)
    store.reinitComponents(changedComponentNames, changedStorageSet, notReloadableComponents)
    return@withContext !notReloadableComponents.isEmpty() && askToRestart(store, notReloadableComponents, null, true)
  }
}

private fun shouldReloadSchemeManager(schemeManager: SchemeManagerImpl<*, *>, pathsToCheck: Collection<String>): Boolean {
  return pathsToCheck.any {
    val path = toIdeaPath(it)
    val fileSpec = schemeManager.fileSpec
    fileSpec == path || path.startsWith("$fileSpec/")
  }
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

enum class SyncType(@PropertyKey(resourceBundle = BUNDLE) val messageKey: String) {
  MERGE("action.MergeSettings.text"),
  OVERWRITE_LOCAL("action.ResetToTheirsSettings.text"),
  OVERWRITE_REMOTE("action.ResetToMySettings.text")
}

class NoRemoteRepositoryException(cause: Throwable) : RuntimeException(cause.message, cause)

class CannotResolveConflictInTestMode : RuntimeException()