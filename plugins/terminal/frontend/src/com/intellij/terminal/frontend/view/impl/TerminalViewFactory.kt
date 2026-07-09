package com.intellij.terminal.frontend.view.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.session.TerminalSessionsManager
import com.intellij.terminal.frontend.session.TerminalTabsManager
import com.intellij.terminal.frontend.toolwindow.impl.TerminalRequestedProcessOptions
import com.intellij.terminal.frontend.toolwindow.impl.updateBackendTabNameOnTitleChange
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.portForwarding.installPortForwarding
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.ui.initOnShow
import com.jediterm.core.util.TermSize
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionId
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils

/**
 * Creates the [TerminalView], starts the terminal process according to [options],
 * and attaches the terminal session to the [TerminalView].
 *
 * Lifecycle of both [TerminalView] and started terminal process are bound to the [coroutineScope].
 */
internal fun createTerminalView(
  project: Project,
  options: TerminalViewBuilderOptions,
  existingBackendTabId: Int?,
  coroutineScope: CoroutineScope,
): TerminalView {
  val terminalView = TerminalViewImpl(
    project = project,
    settings = JBTerminalSystemSettingsProvider(),
    startupFusInfo = options.startupFusInfo,
    coroutineScope = coroutineScope,
    sourceNavigationProjectPath = options.sourceNavigationProjectPath,
  )
  createBackendTabAndStartSession(project, terminalView, options, existingBackendTabId)
  return terminalView
}

@OptIn(AwaitCancellationAndInvoke::class)
private fun createBackendTabAndStartSession(
  project: Project,
  terminal: TerminalViewImpl,
  options: TerminalViewBuilderOptions,
  existingBackendTabId: Int?,
) = terminal.coroutineScope.launch {
  val backendTabId = existingBackendTabId ?: TerminalTabsManager.getInstance(project).createNewTerminalTab().id

  terminal.coroutineScope.awaitCancellationAndInvoke(Dispatchers.EDT) {
    TerminalTabsManager.getInstance(project).closeTerminalTab(backendTabId)
  }

  // Ideally, the backend tab should be under the tab scope, but now it has the lifecycle of the terminal scope
  updateBackendTabNameOnTitleChange(
    terminal,
    backendTabId,
    project,
    scope = terminal.coroutineScope.childScope("Backend tab name updating")
  )

  scheduleSessionStart(project, terminal, options, backendTabId)
}

private suspend fun scheduleSessionStart(
  project: Project,
  terminal: TerminalViewImpl,
  options: TerminalViewBuilderOptions,
  backendTabId: Int,
) {
  if (options.deferSessionStartUntilUiShown) {
    withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
      // Non-cancellable because we expect it to be called only once even if the component was hidden immediately.
      terminal.component.initOnShow("Terminal Session start", context = NonCancellable) {
        doScheduleSessionStart(project, terminal, options.processOptions, backendTabId, calculateSizeFromComponent = true)
      }
    }
  }
  else {
    doScheduleSessionStart(project, terminal, options.processOptions, backendTabId, calculateSizeFromComponent = false)
  }
}

private fun doScheduleSessionStart(
  project: Project,
  terminal: TerminalViewImpl,
  processOptions: TerminalRequestedProcessOptions,
  backendTabId: Int,
  calculateSizeFromComponent: Boolean,
) = terminal.coroutineScope.launch(CoroutineName("Terminal Session start")) {
  val options = prepareStartupOptions(terminal, processOptions, calculateSizeFromComponent)
  val sessionTab = TerminalTabsManager.getInstance(project).startTerminalSessionForTab(backendTabId, options)
  connectSessionToTerminal(project, terminal, sessionTab.sessionId!!)
}

private suspend fun prepareStartupOptions(
  terminal: TerminalView,
  processOptions: TerminalRequestedProcessOptions,
  calculateSizeFromComponent: Boolean,
): ShellStartupOptions {
  val baseOptions = ShellStartupOptions.Builder()
    .shellCommand(processOptions.shellCommand)
    .workingDirectory(processOptions.workingDirectory)
    .envVariables(processOptions.envVariables)
    .processType(processOptions.processType)

  return if (calculateSizeFromComponent) {
    withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
      TerminalUiUtils.getComponentSizeInitializedFuture(terminal.component).await()
      val termSize = terminal.gridSize?.let { TermSize(it.columns, it.rows) }
      baseOptions.initialTermSize(termSize).build()
    }
  }
  else {
    baseOptions.initialTermSize(TermSize(80, 20)).build()
  }
}

private suspend fun connectSessionToTerminal(
  project: Project,
  terminal: TerminalViewImpl,
  sessionId: TerminalSessionId,
) = withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
  val session = TerminalSessionsManager.getInstance(project).getSession(sessionId)
                ?: error("Failed to find TerminalSession with ID: $sessionId")
  terminal.connectToSession(session)

  installPortForwarding(terminal, terminal.coroutineScope.childScope("PortForwarding"))
}