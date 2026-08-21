package com.intellij.terminal.frontend.session.jediterm

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.TerminalExecutorServiceManagerImpl
import com.intellij.terminal.frontend.session.ObservableTtyConnector
import com.intellij.terminal.frontend.session.TerminalShellIntegrationController
import com.intellij.terminal.frontend.session.TerminalShellIntegrationStatisticsListener
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import com.jediterm.core.typeahead.TerminalTypeAheadManager
import com.jediterm.terminal.TerminalExecutorServiceManager
import com.jediterm.terminal.TerminalMode
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
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.session.impl.TerminalSessionTerminatedEvent
import org.jetbrains.plugins.terminal.util.closeConnectorAndStopEmulation

@OptIn(AwaitCancellationAndInvoke::class)
internal fun createJediTerminalSession(
  project: Project?,
  ttyConnector: TtyConnector,
  options: ShellStartupOptions,
  settings: JBTerminalSystemSettingsProviderBase,
  coroutineScope: CoroutineScope,
): TerminalSession {
  val observableTtyConnector = ttyConnector as? ObservableTtyConnector ?: ObservableTtyConnector(ttyConnector)

  val maxHistoryLinesCount = AdvancedSettings.getInt("terminal.buffer.max.lines.count")
  val services: JediTermServices = createJediTermServices(observableTtyConnector, options, maxHistoryLinesCount, settings)

  val outputScope = coroutineScope.childScope("Terminal output forwarding")
  val shellIntegrationController = TerminalShellIntegrationController()
  services.controller.addCustomCommandListener { shellIntegrationController.processCustomCommand(it) }
  if (project != null) {
    shellIntegrationController.addListener(TerminalShellIntegrationStatisticsListener(project))
  }
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
        try {
          outputFlow.emit(listOf(TerminalSessionTerminatedEvent))
        }
        finally {
          coroutineScope.cancel()
        }
      }
    }
  }

  // For the case when coroutine scope is canceled externally
  coroutineScope.awaitCancellationAndInvoke {
    services.terminalStarter.closeConnectorAndStopEmulation()
  }

  return JediTerminalSession(
    inputChannel = inputChannel,
    outputFlow = outputFlow.asSharedFlow(),
    coroutineScope = coroutineScope,
    ttyConnector = ttyConnector,
    terminalDisplay = services.terminalDisplay,
    terminal = services.controller,
  )
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
  controller.setModeEnabled(TerminalMode.AltSendsEscape, settings.altSendsEscape())
  controller.setUrlHyperlinkFilter(JediTermOsc8HyperlinkFilter())

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
    LOG.error(t)
  }
  finally {
    try {
      terminalStarter.ttyConnector.close()
    }
    catch (t: Throwable) {
      LOG.error("Error closing TtyConnector", t)
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

private val LOG = fileLogger()