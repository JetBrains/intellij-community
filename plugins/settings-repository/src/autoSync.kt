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

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.notification.NotificationsAdapter
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationAdapter
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier
import java.util.concurrent.Future

class AutoSyncManager(private val icsManager: IcsManager) {
  private volatile var autoSyncFuture: Future<*>? = null

  fun waitAutoSync(indicator: ProgressIndicator) {
    val autoFuture = autoSyncFuture
    if (autoFuture != null) {
      if (autoFuture.isDone()) {
        autoSyncFuture = null
      }
      else if (autoSyncFuture != null) {
        LOG.info("Wait for auto sync future")
        indicator.setText("Wait for auto sync completion")
        while (!autoFuture.isDone()) {
          if (indicator.isCanceled()) {
            return
          }
          Thread.sleep(5)
        }
      }
    }
  }

  fun registerListeners(application: Application) {
    application.addApplicationListener(object : ApplicationAdapter() {
      override fun applicationExiting() {
        autoSync(true)
      }
    })
  }

  fun registerListeners(project: Project) {
    project.getMessageBus().connect().subscribe(Notifications.TOPIC, object : NotificationsAdapter() {
      override fun notify(notification: Notification) {
        if (!icsManager.repositoryActive || project.isDisposed()) {
          return
        }

        if (when {
          notification.getGroupId() == VcsBalloonProblemNotifier.NOTIFICATION_GROUP.getDisplayId() -> {
            val message = notification.getContent()
            message.startsWith("VCS Update Finished") ||
              message == VcsBundle.message("message.text.file.is.up.to.date") ||
              message == VcsBundle.message("message.text.all.files.are.up.to.date")
          }

          notification.getGroupId() == VcsNotifier.NOTIFICATION_GROUP_ID.getDisplayId() && notification.getTitle() == "Push successful" -> true

          else -> false
        }) {
          autoSync()
        }
      }
    })
  }

  fun autoSync(onAppExit: Boolean = false) {
    if (!icsManager.repositoryActive) {
      return
    }

    var future = autoSyncFuture
    if (future != null && !future.isDone()) {
      return
    }

    val app = ApplicationManagerEx.getApplicationEx() as ApplicationImpl

    if (onAppExit) {
      sync(app, onAppExit)
      return
    }
    else if (app.isDisposeInProgress()) {
      // will be handled by applicationExiting listener
      return
    }

    future = app.executeOnPooledThread {
      if (autoSyncFuture == future) {
        // to ensure that repository will not be in uncompleted state and changes will be pushed
        ShutDownTracker.getInstance().registerStopperThread(Thread.currentThread())
        try {
          sync(app, onAppExit)
        }
        finally {
          autoSyncFuture = null
          ShutDownTracker.getInstance().unregisterStopperThread(Thread.currentThread())
        }
      }
    }
    autoSyncFuture = future
  }

  private fun sync(app: ApplicationImpl, onAppExit: Boolean) {
    catchAndLog {
      icsManager.runInAutoCommitDisabledMode {
        val repositoryManager = icsManager.repositoryManager
        if (!repositoryManager.canCommit()) {
          LOG.warn("Auto sync skipped: repository is not committable")
          return@runInAutoCommitDisabledMode
        }

        // on app exit fetch and push only if there are commits to push
        if (onAppExit && !repositoryManager.commit() && repositoryManager.getAheadCommitsCount() == 0) {
          return@runInAutoCommitDisabledMode
        }

        val updater = repositoryManager.fetch()
        // we merge in EDT non-modal to ensure that new settings will be properly applied
        app.invokeAndWait({
          catchAndLog {
            val updateResult = updater.merge()
            if (!onAppExit && !app.isDisposeInProgress() && updateResult != null && updateStoragesFromStreamProvider(app.getStateStore(), updateResult)) {
              // force to avoid saveAll & confirmation
              app.exit(true, true, true, true)
            }
          }
        }, ModalityState.NON_MODAL)

        if (!updater.definitelySkipPush) {
          repositoryManager.push()
        }
      }
    }
  }
}

inline fun catchAndLog(runnable: () -> Unit) {
  try {
    runnable()
  }
  catch (e: ProcessCanceledException) {
  }
  catch (e: Throwable) {
    if (e is AuthenticationException || e is NoRemoteRepositoryException) {
      LOG.warn(e)
    }
    else {
      LOG.error(e)
    }
  }
}