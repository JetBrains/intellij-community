// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.settingsRepository

import com.intellij.configurationStore.ComponentStoreImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.platform.util.progress.reportRawProgress
import kotlinx.coroutines.*

internal class AutoSyncManager(private val icsManager: IcsManager) {
  @Volatile
  private var autoSyncFuture: Job? = null

  @Volatile var enabled = true

  suspend fun waitAutoSync() = reportRawProgress { reporter ->
    val autoFuture = autoSyncFuture ?: return
    if (autoFuture.isCompleted) {
      autoSyncFuture = null
    }
    else {
      LOG.info("Wait for auto sync future")
      reporter.text(IcsBundle.message("autosync.progress.text"))
      autoFuture.join()
    }
  }

  fun autoSync(onAppExit: Boolean = false, force: Boolean = false) {
    if (!enabled || !icsManager.isActive || (!force && !icsManager.settings.autoSync)) {
      return
    }

    autoSyncFuture?.let {
      if (!it.isCompleted) {
        return
      }
    }

    if (onAppExit) {
      // called on final confirmed exit - no need to restore enabled state
      enabled = false
      catchAndLog {
        runBlockingMaybeCancellable {
          icsManager.runInAutoCommitDisabledMode {
            val repositoryManager = icsManager.repositoryManager
            val hasUpstream = repositoryManager.hasUpstream()
            if (hasUpstream && !repositoryManager.canCommit()) {
              LOG.warn("Auto sync skipped: repository is not committable")
              return@runInAutoCommitDisabledMode
            }

            // on app exit fetch and push only if there are commits to push
            // if no upstream - just update cloud schemes
            if (hasUpstream && !repositoryManager.commit() && repositoryManager.getAheadCommitsCount() == 0 && icsManager.readOnlySourcesManager.repositories.isEmpty()) {
              return@runInAutoCommitDisabledMode
            }

            // use explicit progress task to sync on app exit to make it clear why app is not exited immediately
            icsManager.syncManager.sync(SyncType.MERGE, onAppExit = true)
          }
        }
      }
      return
    }
    else if (ApplicationManager.getApplication().isDisposed) {
      // will be handled by applicationExiting listener
      return
    }

    autoSyncFuture = ApplicationManager.getApplication().coroutineScope.launch {
      catchAndLog {
        icsManager.runInAutoCommitDisabledMode {
          doSync()
        }
      }
    }
  }

  private suspend fun doSync() {
    val app = ApplicationManager.getApplication()
    val repositoryManager = icsManager.repositoryManager
    val hasUpstream = repositoryManager.hasUpstream()
    if (hasUpstream && !repositoryManager.canCommit()) {
      LOG.warn("Auto sync skipped: repository is not committable")
      return
    }

    // update read-only sources at first (because contain scheme - to ensure that some scheme will exist when it will be set as current by some setting)
    updateCloudSchemes(icsManager)

    if (!hasUpstream) {
      return
    }

    val updater = repositoryManager.fetch()
    // we merge in EDT non-modal to ensure that new settings will be properly applied
    withContext(Dispatchers.EDT + ModalityState.nonModal().asContextElement()) {
      catchAndLog {
        val updateResult = updater.merge()
        if (!app.isDisposed &&
            updateResult != null &&
            updateStoragesFromStreamProvider(icsManager, app.stateStore as ComponentStoreImpl, updateResult)) {
          // force to avoid saveAll & confirmation
          app.exit(true, true, true)
        }
      }
    }

    if (!updater.definitelySkipPush) {
      repositoryManager.push()
    }
  }
}

internal inline fun catchAndLog(asWarning: Boolean = false, runnable: () -> Unit) {
  try {
    runnable()
  }
  catch (ignored: ProcessCanceledException) {
  }
  catch (ignored: CancellationException) {
  }
  catch (e: Throwable) {
    if (asWarning || e is AuthenticationException || e is NoRemoteRepositoryException) {
      LOG.warn(e)
    }
    else {
      LOG.error(e)
    }
  }
}