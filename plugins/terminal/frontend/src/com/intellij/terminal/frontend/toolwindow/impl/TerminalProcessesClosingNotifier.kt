package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.execution.TerminateRemoteProcessDialog
import com.intellij.execution.TerminateRemoteProcessDialog.ProcessCloseConfirmationResult
import com.intellij.execution.process.NopProcessHandler
import com.intellij.execution.ui.RunContentManagerImpl
import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.VetoableProjectManagerListener
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Key
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import java.time.LocalDateTime

/**
 * Checks all opened terminal tabs (both reworked and classic) during IDE/project closing
 * and shows a single confirmation dialog listing if there tabs that require confirmation.
 * The project is allowed to close only if the user agrees to terminate the processes.
 *
 * This class is responsible only for the notification,
 * actual processes termination is performed in [com.intellij.terminal.frontend.session.TerminalSessionsManager].
 *
 * Note that similar confirmation logic is performed in [org.jetbrains.plugins.terminal.TerminalTabCloseListener].
 * But it is applied in a different context (should never intersect with the logic in this class).
 * TerminalTabCloseListener runs when the user closes a single terminal tab, and the project is not closing at this moment.
 */
internal object TerminalProcessesClosingNotifier : VetoableProjectManagerListener, ApplicationListener {
  private val PROCESSES_TERMINATION_CONFIRMED_TIME_KEY = Key<LocalDateTime>("TERMINAL_PROCESSES_TERMINATION_CONFIRMED_TIME")

  /**
   * Have to check all opened projects there
   * to show our confirmation dialog before "Stop the IDE Backend or Keep It Running" RemDev dialog from
   * `com.jetbrains.thinclient.unattendedHost.closeNotification.UnattendedHostClientApplicationListener`.
   */
  override fun canExitApplication(): Boolean {
    val projectManager = ProjectManager.getInstanceIfCreated() ?: return true
    for (project in projectManager.openProjects) {
      if (!canClose(project)) {
        return false
      }
    }
    return true
  }

  override fun canClose(project: Project): Boolean {
    if (!IdeProductMode.isFrontend) {
      // Processes closing notification is enabled only in RemDev for now
      return true
    }

    // This logic can be called multiple times during the IDE closing process:
    // during `canExitApplication` and `canClose` checks in `ApplicationImpl.canExit`,
    // then during `canClose` check in `ProjectManagerImpl.closeProject`.
    // So, let's do not show the confirmation dialog again if the user already confirmed processes termination.
    // Consider the confirmation valid for 1 minute.
    val lastConfirmedTime = project.getUserData(PROCESSES_TERMINATION_CONFIRMED_TIME_KEY)
    if (lastConfirmedTime != null && !LocalDateTime.now().isAfter(lastConfirmedTime.plusMinutes(1))) {
      return true
    }

    // canClose() is invoked on EDT during project close, so reading the tool window tabs here is safe.
    // Use serviceIfCreated to avoid creating a terminal service just to close a project without terminals.
    val reworkedViews = (project.serviceIfCreated<TerminalToolWindowTabsManager>()?.tabs ?: emptyList()).map { it.view }
    val classicWidgets = (project.serviceIfCreated<TerminalToolWindowManager>()?.terminalWidgets?.toList() ?: emptyList())
    if (reworkedViews.isEmpty() && classicWidgets.isEmpty()) {
      return true
    }

    val tabTitlesToConfirm = runWithModalProgressBlocking(project, "") {
      collectTabTitlesToConfirm(reworkedViews, classicWidgets)
    }
    if (tabTitlesToConfirm.isEmpty()) {
      return true
    }

    val terminationConfirmed = confirmTermination(project, tabTitlesToConfirm)
    if (terminationConfirmed) {
      project.putUserData(PROCESSES_TERMINATION_CONFIRMED_TIME_KEY, LocalDateTime.now())
    }
    return terminationConfirmed
  }

  private suspend fun collectTabTitlesToConfirm(
    reworkedTabs: List<TerminalView>,
    classicWidgets: List<TerminalWidget>,
  ): List<String> = coroutineScope {
    val tasks = mutableListOf<Deferred<String?>>()

    for (view in reworkedTabs) {
      tasks += async {
        if (TerminalTabCloseListenerImpl.shouldConfirmClosing(view)) {
          view.getFullTitleText()
        }
        else null
      }
    }
    for (widget in classicWidgets) {
      tasks += async(Dispatchers.IO) {
        if (widget.isCommandRunning()) {
          widget.terminalTitle.buildFullTitle()
        }
        else null
      }
    }

    tasks.awaitAll().filterNotNull()
  }

  private fun confirmTermination(project: Project, tabTitles: List<String>): Boolean {
    val fakeProcesses = List(tabTitles.size) {
      NopProcessHandler().also {
        it.startNotify()
        it.putUserData(RunContentManagerImpl.ALWAYS_USE_DEFAULT_STOPPING_BEHAVIOUR_KEY, true)
      }
    }
    return TerminateRemoteProcessDialog.show(project, tabTitles, fakeProcesses) != ProcessCloseConfirmationResult.LEAVE_RUNNING
  }
}

internal class TerminalProcessesClosingNotifierInstaller(private val coroutineScope: CoroutineScope) : AppLifecycleListener,
                                                                                                       ProjectActivity {
  init {
    val application = ApplicationManager.getApplication()
    if (application.isUnitTestMode || application.isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }
  }

  /**
   * Have to install our listener as early as possible to be before
   * `com.jetbrains.thinclient.unattendedHost.closeNotification.UnattendedHostClientApplicationListener` installation.
   * So, our confirmation dialog is shown before the "Stop the IDE Backend or Keep It Running" RemDev dialog.
   */
  override fun appStarted() {
    ApplicationManager.getApplication().addApplicationListener(TerminalProcessesClosingNotifier, coroutineScope.asDisposable())
  }

  override suspend fun execute(project: Project) {
    ProjectManager.getInstance().addProjectManagerListener(project, TerminalProcessesClosingNotifier)
  }
}