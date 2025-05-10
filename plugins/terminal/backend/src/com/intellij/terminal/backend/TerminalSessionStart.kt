// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.backend

import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.TerminalExecutorServiceManagerImpl
import com.intellij.terminal.session.TerminalSession
import com.intellij.terminal.session.TerminalSessionTerminatedEvent
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import com.jediterm.core.typeahead.TerminalTypeAheadManager
import com.jediterm.terminal.TerminalExecutorServiceManager
import com.jediterm.terminal.TerminalStarter
import com.jediterm.terminal.TtyBasedArrayDataStream
import com.jediterm.terminal.TtyConnector
import com.jediterm.terminal.model.JediTermTypeAheadModel
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalTextBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.LocalBlockTerminalRunner
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.util.STOP_EMULATOR_TIMEOUT
import org.jetbrains.plugins.terminal.util.waitFor

internal fun startTerminalProcess(
  project: Project,
  options: ShellStartupOptions,
): Pair<TtyConnector, ShellStartupOptions> {
  val runner = LocalBlockTerminalRunner(project)
  val configuredOptions = runner.configureStartupOptions(options)
  val process = runner.createProcess(configuredOptions)
  val connector = runner.createTtyConnector(process)

  return connector to configuredOptions
}

/**
 * Returns a pair of started terminal session and final options used for session start.
 */
@OptIn(AwaitCancellationAndInvoke::class)
internal fun createTerminalSession(
  project: Project,
  ttyConnector: TtyConnector,
  options: ShellStartupOptions,
  settings: JBTerminalSystemSettingsProviderBase,
  coroutineScope: CoroutineScope,
): TerminalSession {
  val observableTtyConnector = ttyConnector as? ObservableTtyConnector ?: ObservableTtyConnector(ttyConnector)

  val maxHistoryLinesCount = AdvancedSettings.getInt("terminal.buffer.max.lines.count")
  val services: JediTermServices = createJediTermServices(observableTtyConnector, options, maxHistoryLinesCount, settings)

  val outputScope = coroutineScope.childScope("Terminal output forwarding")
  val shellIntegrationController = TerminalShellIntegrationController(services.controller)
  shellIntegrationController.addListener(TerminalShellIntegrationStatisticsListener(project))
  val outputFlow = createTerminalOutputFlow(
    services,
    shellIntegrationController,
    outputScope,
  )

  val inputScope = coroutineScope.childScope("Terminal input handling")
  val inputChannel = createTerminalInputChannel(services, inputScope)

  services.executorService.unboundedExecutorService.submit {
    try {
      startTerminalEmulation(services.terminalStarter)
    }
    finally {
      coroutineScope.launch {
        outputFlow.emit(listOf(TerminalSessionTerminatedEvent))
        coroutineScope.cancel()
      }
    }
  }

  // For the case when coroutine scope is canceled externally
  coroutineScope.awaitCancellationAndInvoke {
    val starter = services.terminalStarter
    starter.close() // close in background
    starter.ttyConnector.waitFor(STOP_EMULATOR_TIMEOUT) {
      starter.requestEmulatorStop()
    }
  }

  return BackendTerminalSession(inputChannel, outputFlow.asSharedFlow())
}

private fun createJediTermServices(
  connector: ObservableTtyConnector,
  options: ShellStartupOptions,
  maxHistoryLinesCount: Int,
  settings: JBTerminalSystemSettingsProviderBase,
): JediTermServices {
  val styleState = StyleState()
  val initialSize = options.initialTermSize ?: error("Initial term size must be set")
  val textBuffer = TerminalTextBuffer(initialSize.columns, initialSize.rows, styleState, maxHistoryLinesCount)
  val terminalDisplay = TerminalDisplayImpl(settings)
  val controller = ObservableJediTerminal(terminalDisplay, textBuffer, styleState)
  val typeAheadManager = TerminalTypeAheadManager(JediTermTypeAheadModel(controller, textBuffer, settings))
  val executorService = TerminalExecutorServiceManagerImpl()
  val terminalStarter = TerminalStarterEx(
    controller,
    connector,
    TtyBasedArrayDataStream(connector),
    typeAheadManager,
    executorService
  )

  return JediTermServices(textBuffer, terminalDisplay, controller, executorService, terminalStarter, connector, options)
}

private fun startTerminalEmulation(terminalStarter: TerminalStarter) {
  try {
    terminalStarter.start()
  }
  catch (t: Throwable) {
    BackendTerminalSession.LOG.error(t)
  }
  finally {
    try {
      terminalStarter.ttyConnector.close()
    }
    catch (t: Throwable) {
      BackendTerminalSession.LOG.error("Error closing TtyConnector", t)
    }
  }
}

internal class JediTermServices(
  val textBuffer: TerminalTextBuffer,
  val terminalDisplay: TerminalDisplayImpl,
  val controller: ObservableJediTerminal,
  val executorService: TerminalExecutorServiceManager,
  val terminalStarter: TerminalStarterEx,
  val ttyConnector: ObservableTtyConnector,
  val startupOptions: ShellStartupOptions,
)
