package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.help.impl.HelpManagerImpl
import com.intellij.ide.BrowserUtil
import com.intellij.ide.IdleTracker
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.UI
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.frontend.toolwindow.TerminalTabsManagerListener
import com.intellij.terminal.frontend.view.TerminalView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.ExperimentalTerminalMigration
import org.jetbrains.plugins.terminal.TERMINAL_CONFIGURABLE_ID
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandExecutionListener
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandFinishedEvent
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalShellIntegration
import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

@OptIn(FlowPreview::class)
internal class ExperimentalTerminalMigrationNotifier(private val project: Project) : TerminalTabsManagerListener {
  override fun terminalViewCreated(view: TerminalView) {
    if (!ExperimentalTerminalMigration.shouldShowEngineChangeNotification()) return

    view.coroutineScope.launch {
      awaitCommandExecutedAndShowNotification(view)
    }
  }

  private suspend fun awaitCommandExecutedAndShowNotification(view: TerminalView) {
    val shellIntegration = view.shellIntegrationDeferred.await()

    // Wait for command executed - user started working in the terminal.
    shellIntegration.awaitCommandExecuted()
    // Wait for a user stopped working, was idle, and returned
    awaitUserIdleAndReturned()

    withContext(Dispatchers.UI) {
      if (ExperimentalTerminalMigration.shouldShowEngineChangeNotification()) {
        showEngineChangeNotification(project)
        ExperimentalTerminalMigration.setEngineChangeNotificationShown()
      }
    }
  }

  private suspend fun TerminalShellIntegration.awaitCommandExecuted() {
    suspendCancellableCoroutine { continuation ->
      val disposable = Disposer.newDisposable()
      continuation.invokeOnCancellation { Disposer.dispose(disposable) }
      addCommandExecutionListener(disposable, object : TerminalCommandExecutionListener {
        override fun commandFinished(event: TerminalCommandFinishedEvent) {
          Disposer.dispose(disposable)
          continuation.resume(Unit)
        }
      })
    }
  }

  private suspend fun awaitUserIdleAndReturned() {
    withContext(Dispatchers.UI) {
      // Wait for user idle 5 seconds
      getUserActivityFlow()
        .debounce(5.seconds)
        .first()
      // Wait for the first action after user idle (but drop one replayed event)
      getUserActivityFlow().drop(1).first()
    }
  }

  private fun getUserActivityFlow(): Flow<Unit> {
    val mouseMovementFlow = channelFlow {
      val listener = AWTEventListener {
        trySend(Unit)
      }
      Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.MOUSE_MOTION_EVENT_MASK or AWTEvent.MOUSE_WHEEL_EVENT_MASK)

      awaitClose {
        Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
      }
    }

    return merge(IdleTracker.getInstance().events, mouseMovementFlow)
  }

  fun showEngineChangeNotification(project: Project) {
    val configureAction = NotificationAction.createSimple(TerminalBundle.message("exp.terminal.switch.notification.open.settings")) {
      ShowSettingsUtilImpl.showSettingsDialog(
        project,
        idToSelect = TERMINAL_CONFIGURABLE_ID,
        filter = null,
      )
    }

    val terminalPageUrl = HelpManagerImpl.getHelpUrl("terminal-emulator")
    val terminalEnginePageUrl = terminalPageUrl?.let { "$it#terminal-engine" }
    val learnMoreAction = if (terminalEnginePageUrl != null) {
      NotificationAction.createSimple(TerminalBundle.message("exp.terminal.switch.notification.learn.more")) {
        BrowserUtil.browse(terminalEnginePageUrl)
      }
    }
    else null

    NotificationGroupManager.getInstance()
      .getNotificationGroup("terminal")
      .createNotification(
        TerminalBundle.message("exp.terminal.switch.notification.title"),
        TerminalBundle.message("exp.terminal.switch.notification.content"),
        NotificationType.INFORMATION,
      )
      .addActions(listOfNotNull(configureAction, learnMoreAction))
      .notify(project)
  }
}
