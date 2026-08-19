package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.VetoableProjectManagerListener
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.tabInEditor.ToolWindowEditorTabFile
import com.intellij.openapi.wm.impl.tabInEditor.ToolWindowEditorTabManager
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.content.Content
import com.intellij.util.asDisposable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
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
    val terminalTabs = collectTerminalTabs(project)
    if (terminalTabs.isEmpty()) {
      return true
    }

    val tabTitlesToConfirm = try {
      runWithModalProgressBlocking(project, TerminalBundle.message("checking.running.terminal.processes.progress")) {
        collectTabTitlesToConfirm(terminalTabs)
      }
    }
    catch (_: CancellationException) {
      ProgressManager.checkCanceled()
      // User pressed "cancel" in the progress dialog.
      // Since the user's intention is to close the project,
      // consider that the user wants to skip any additional checks and finally close the project.
      project.putUserData(PROCESSES_TERMINATION_CONFIRMED_TIME_KEY, LocalDateTime.now())
      return true
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

  private fun collectTerminalTabs(project: Project): List<TerminalTabContent> {
    val contents = getTerminalToolWindowContents(project) + getTerminalEditorTabContents(project)
    return contents.mapNotNull { it.toTerminalTabContentOrNull() }
  }

  private fun getTerminalToolWindowContents(project: Project): List<Content> {
    val terminalToolWindow = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)
                             ?: return emptyList()
    // Use `contentManagerIfCreated` to avoid initializing the tool window if it is not yet created.
    return terminalToolWindow.contentManagerIfCreated?.contentsRecursively ?: emptyList()
  }

  private fun getTerminalEditorTabContents(project: Project): List<Content> {
    // Use `serviceIfCreated` to avoid initializing the services if they are not yet created.
    val fileEditorManager = project.serviceIfCreated<FileEditorManager>()
    val editorTabsManager = project.serviceIfCreated<ToolWindowEditorTabManager>()
    if (fileEditorManager == null || editorTabsManager == null) {
      // If these services are not yet created, there are definitely no related editor tabs.
      return emptyList()
    }

    return fileEditorManager.openFiles
      .asSequence()
      .filterIsInstance<ToolWindowEditorTabFile>()
      .filter { file -> file.toolWindowId == TerminalToolWindowFactory.TOOL_WINDOW_ID }
      .mapNotNull { file -> editorTabsManager.getSession(file) }
      .map { session -> session.content }
      .toList()
  }

  private suspend fun collectTabTitlesToConfirm(tabs: List<TerminalTabContent>): List<String> = coroutineScope {
    val tasks = tabs.map {
      async {
        it.getClosingConfirmationDetails()
      }
    }
    tasks.awaitAll().mapNotNull { it?.fullTitle }
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