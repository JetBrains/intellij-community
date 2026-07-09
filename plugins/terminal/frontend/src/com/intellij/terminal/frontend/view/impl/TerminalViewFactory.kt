package com.intellij.terminal.frontend.view.impl

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.session.TerminalSessionsManager
import com.intellij.terminal.frontend.toolwindow.impl.TerminalRequestedProcessOptions
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.portForwarding.installPortForwarding
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
  coroutineScope: CoroutineScope,
): TerminalView {
  val terminalView = TerminalViewImpl(
    project = project,
    settings = JBTerminalSystemSettingsProvider(),
    startupFusInfo = options.startupFusInfo,
    coroutineScope = coroutineScope,
    sourceNavigationProjectPath = options.sourceNavigationProjectPath,
  )
  scheduleSessionStart(project, terminalView, options)
  return terminalView
}

private fun scheduleSessionStart(
  project: Project,
  terminal: TerminalViewImpl,
  options: TerminalViewBuilderOptions,
) = terminal.coroutineScope.launch {
  if (options.deferSessionStartUntilUiShown) {
    withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
      // Non-cancellable because we expect it to be called only once even if the component was hidden immediately.
      terminal.component.initOnShow("Terminal Session start", context = NonCancellable) {
        doScheduleSessionStart(project, terminal, options.processOptions, calculateSizeFromComponent = true)
      }
    }
  }
  else {
    doScheduleSessionStart(project, terminal, options.processOptions, calculateSizeFromComponent = false)
  }
}

private fun doScheduleSessionStart(
  project: Project,
  terminal: TerminalViewImpl,
  processOptions: TerminalRequestedProcessOptions,
  calculateSizeFromComponent: Boolean,
) = terminal.coroutineScope.launch(CoroutineName("Terminal Session start")) {
  val options = prepareStartupOptions(terminal, processOptions, calculateSizeFromComponent)

  val sessionScope = terminal.coroutineScope.childScope("TerminalSession")
  val session = TerminalSessionsManager.getInstance(project).startSession(options, sessionScope).session
  terminal.connectToSession(session)

  installPortForwarding(terminal, terminal.coroutineScope.childScope("PortForwarding"))
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