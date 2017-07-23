/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.notification.NotificationsAdapter
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier
import java.util.concurrent.Future

internal class AutoSyncManager(private val icsManager: IcsManager) {
  private @Volatile var autoSyncFuture: Future<*>? = null

  @Volatile var enabled = true

  fun waitAutoSync(indicator: ProgressIndicator) {
    val autoFuture = autoSyncFuture
    if (autoFuture != null) {
      if (autoFuture.isDone) {
        autoSyncFuture = null
      }
      else if (autoSyncFuture != null) {
        LOG.info("Wait for auto sync future")
        indicator.text = "Wait for auto sync completion"
        while (!autoFuture.isDone) {
          if (indicator.isCanceled) {
            return
          }
          Thread.sleep(5)
        }
      }
    }
  }

  fun registerListeners(project: Project) {
    project.messageBus.connect().subscribe(Notifications.TOPIC, object : NotificationsAdapter() {
      override fun notify(notification: Notification) {
        if (!icsManager.active) {
          return
        }

        if (when {
          notification.groupId == VcsBalloonProblemNotifier.NOTIFICATION_GROUP.displayId -> {
            val message = notification.content
            message.startsWith("VCS Update Finished") ||
              message == VcsBundle.message("message.text.file.is.up.to.date") ||
              message == VcsBundle.message("message.text.all.files.are.up.to.date")
          }

          notification.groupId == VcsNotifier.NOTIFICATION_GROUP_ID.displayId && notification.title == "Push successful" -> true

          else -> false
        }) {
          autoSync()
        }
      }
    })
  }

  fun autoSync(onAppExit: Boolean = false, force: Boolean = false) {
    if (!enabled || !icsManager.active || (!force && !icsManager.settings.autoSync)) {
      return
    }

    autoSyncFuture?.let {
      if (!it.isDone) {
        return
      }
    }

    val app = ApplicationManagerEx.getApplicationEx() as ApplicationImpl

    if (onAppExit) {
      sync(app, onAppExit)
      return
    }
    else if (app.isDisposeInProgress) {
      // will be handled by applicationExiting listener
      return
    }

    autoSyncFuture = app.executeOnPooledThread {
      try {
        // to ensure that repository will not be in uncompleted state and changes will be pushed
        ShutDownTracker.getInstance().registerStopperThread(Thread.currentThread())
        sync(app, onAppExit)
      }
      finally {
        autoSyncFuture = null
        ShutDownTracker.getInstance().unregisterStopperThread(Thread.currentThread())
      }
    }
  }

  private fun sync(app: ApplicationImpl, onAppExit: Boolean) {
    catchAndLog {
      icsManager.runInAutoCommitDisabledMode {
        doSync(app, onAppExit)
      }
    }
  }

  private fun doSync(app: ApplicationImpl, onAppExit: Boolean) {
    val repositoryManager = icsManager.repositoryManager
    val hasUpstream = repositoryManager.hasUpstream()
    if (hasUpstream && !repositoryManager.canCommit()) {
      LOG.warn("Auto sync skipped: repository is not committable")
      return
    }

    // on app exit fetch and push only if there are commits to push
    if (onAppExit) {
      // if no upstream - just update cloud schemes
      if (hasUpstream && !repositoryManager.commit() && repositoryManager.getAheadCommitsCount() == 0 && icsManager.readOnlySourcesManager.repositories.isEmpty()) {
        return
      }

      // use explicit progress task to sync on app exit to make it clear why app is not exited immediately
      icsManager.syncManager.sync(SyncType.MERGE, onAppExit = true)
      return
    }

    // update read-only sources at first (because contain scheme - to ensure that some scheme will exist when it will be set as current by some setting)
    updateCloudSchemes(icsManager)

    if (hasUpstream) {
      val updater = repositoryManager.fetch()
      // we merge in EDT non-modal to ensure that new settings will be properly applied
      app.invokeAndWait({
                          catchAndLog {
                            val updateResult = updater.merge()
                            if (!onAppExit &&
                                !app.isDisposeInProgress &&
                                updateResult != null &&
                                updateStoragesFromStreamProvider(icsManager, app.stateStore as ComponentStoreImpl, updateResult,
                                                                 app.messageBus)) {
                              // force to avoid saveAll & confirmation
                              app.exit(true, true, true)
                            }
                          }
                        }, ModalityState.NON_MODAL)

      if (!updater.definitelySkipPush) {
        repositoryManager.push()
      }
    }
  }
}

inline internal fun catchAndLog(asWarning: Boolean = false, runnable: () -> Unit) {
  try {
    runnable()
  }
  catch (e: ProcessCanceledException) { }
  catch (e: Throwable) {
    if (asWarning || e is AuthenticationException || e is NoRemoteRepositoryException) {
      LOG.warn(e)
    }
    else {
      LOG.error(e)
    }
  }
}