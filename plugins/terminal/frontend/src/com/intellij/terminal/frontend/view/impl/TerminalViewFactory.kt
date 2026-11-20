// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.impl

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.session.TerminalSessionsManager
import com.intellij.terminal.frontend.toolwindow.TerminalRequestedProcessOptions
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.ketraterm.KetraTermTerminalView
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
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils
import org.jetbrains.plugins.terminal.session.impl.dto.TerminalGridGeometryDto
import org.jetbrains.plugins.terminal.session.toTermSize

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
  val emulatorType = options.processOptions.emulatorType ?: TerminalEmulatorType.default
  if (emulatorType == TerminalEmulatorType.KetraTerm) {
    val view = KetraTermTerminalView(project, JBTerminalSystemSettingsProvider(), coroutineScope)
    scheduleSessionStart(project, view, options, emulatorType, view::connectToSession)
    return view
  }
  val terminalView = TerminalViewImpl(
    project = project,
    settings = JBTerminalSystemSettingsProvider(),
    startupFusInfo = options.startupFusInfo,
    coroutineScope = coroutineScope,
  )
  scheduleSessionStart(project, terminalView, options, emulatorType, terminalView::connectToSession)
  return terminalView
}

private fun scheduleSessionStart(
  project: Project,
  terminal: TerminalView,
  options: TerminalViewBuilderOptions,
  emulatorType: TerminalEmulatorType,
  connect: suspend (TerminalSession) -> Unit,
) = terminal.coroutineScope.launch {
  if (options.deferSessionStartUntilUiShown) {
    withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
      // Non-cancellable because we expect it to be called only once even if the component was hidden immediately.
      terminal.component.initOnShow("Terminal Session start", context = NonCancellable) {
        doScheduleSessionStart(project, terminal, options.processOptions, emulatorType, connect, calculateSizeFromComponent = true)
      }
    }
  }
  else {
    doScheduleSessionStart(project, terminal, options.processOptions, emulatorType, connect, calculateSizeFromComponent = false)
  }
}

private fun doScheduleSessionStart(
  project: Project,
  terminal: TerminalView,
  processOptions: TerminalRequestedProcessOptions,
  emulatorType: TerminalEmulatorType,
  connect: suspend (TerminalSession) -> Unit,
  calculateSizeFromComponent: Boolean,
) = terminal.coroutineScope.launch(CoroutineName("Terminal Session start")) {
  val options = prepareStartupOptions(terminal, processOptions, emulatorType, calculateSizeFromComponent)

  val sessionScope = terminal.coroutineScope.childScope("TerminalSession")
  val session = TerminalSessionsManager.getInstance(project).startSession(options, sessionScope).session
  connect(session)

  installPortForwarding(terminal, terminal.coroutineScope.childScope("PortForwarding"))
}

private suspend fun prepareStartupOptions(
  terminal: TerminalView,
  processOptions: TerminalRequestedProcessOptions,
  emulatorType: TerminalEmulatorType,
  calculateSizeFromComponent: Boolean,
): ShellStartupOptions {
  val baseOptions = ShellStartupOptions.Builder()
    .shellCommand(processOptions.shellCommand)
    .workingDirectory(processOptions.workingDirectory)
    .envVariables(processOptions.envVariables)
    .processType(processOptions.processType)
    .emulatorType(emulatorType)

  return if (calculateSizeFromComponent) {
    baseOptions.initialGridGeometry(terminal.awaitLaidOutGridGeometry()).build()
  }
  else {
    baseOptions.initialTermSize(TermSize(80, 20)).build()
  }
}

/**
 * Waits until the view's component has been laid out and returns the grid geometry it actually has.
 *
 * Starting a process with a guessed size makes a full-screen TUI paint its first frame at the wrong width and
 * reflow on the following resize, so every path that can wait for the UI should take the size from here instead.
 * Returns null when the grid is unknown; the caller then falls back to a default and logs it.
 */
internal suspend fun TerminalView.awaitLaidOutGridGeometry(): TerminalGridGeometryDto? {
  return withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
    TerminalUiUtils.getComponentSizeInitializedFuture(component).await()
    gridGeometry
  }
}

/** Sets the initial term size and the initial cell pixel size from [geometry]; null clears both. */
internal fun ShellStartupOptions.Builder.initialGridGeometry(geometry: TerminalGridGeometryDto?): ShellStartupOptions.Builder {
  return initialTermSize(geometry?.gridSize?.toTermSize())
    .initialCellPixelSize(geometry?.cellSize)
}
