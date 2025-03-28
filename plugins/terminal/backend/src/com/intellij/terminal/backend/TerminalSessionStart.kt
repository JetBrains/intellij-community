// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.backend

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.TerminalExecutorServiceManagerImpl
import com.intellij.terminal.session.*
import com.intellij.terminal.session.dto.toTermSize
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import com.jediterm.core.typeahead.TerminalTypeAheadManager
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.*
import com.jediterm.terminal.model.JediTermTypeAheadModel
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalTextBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.LocalBlockTerminalRunner
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.block.ui.withLock
import org.jetbrains.plugins.terminal.util.STOP_EMULATOR_TIMEOUT
import org.jetbrains.plugins.terminal.util.waitFor
import java.util.concurrent.CancellationException

private val LOG: Logger = Logger.getInstance(BackendTerminalSession::class.java)

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
  initialSize: TermSize,
  settings: JBTerminalSystemSettingsProviderBase,
  coroutineScope: CoroutineScope,
): TerminalSession {

  val maxHistoryLinesCount = AdvancedSettings.getInt("terminal.buffer.max.lines.count")
  val services: JediTermServices = createJediTermServices(ttyConnector, initialSize, maxHistoryLinesCount, settings)

  val outputScope = coroutineScope.childScope("Terminal output forwarding")
  val shellIntegrationController = TerminalShellIntegrationController(services.controller)
  shellIntegrationController.addListener(TerminalShellIntegrationStatisticsListener(project))
  val outputFlow = createTerminalOutputFlow(
    services.textBuffer,
    services.terminalDisplay,
    services.controller,
    shellIntegrationController,
    outputScope,
    ensureEmulationActive = { ensureEmulationActive(services.terminalStarter) }
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
  connector: TtyConnector,
  termSize: TermSize,
  maxHistoryLinesCount: Int,
  settings: JBTerminalSystemSettingsProviderBase,
): JediTermServices {
  val styleState = StyleState()
  val textBuffer = TerminalTextBuffer(termSize.columns, termSize.rows, styleState, maxHistoryLinesCount)
  val terminalDisplay = TerminalDisplayImpl(settings)
  val controller = ObservableJediTerminal(terminalDisplay, textBuffer, styleState)
  val typeAheadManager = TerminalTypeAheadManager(JediTermTypeAheadModel(controller, textBuffer, settings))
  val executorService = TerminalExecutorServiceManagerImpl()
  val terminalStarter = StopAwareTerminalStarter(
    controller,
    connector,
    TtyBasedArrayDataStream(connector),
    typeAheadManager,
    executorService
  )

  return JediTermServices(textBuffer, terminalDisplay, controller, executorService, terminalStarter)
}

private fun createTerminalInputChannel(
  services: JediTermServices,
  coroutineScope: CoroutineScope,
): SendChannel<TerminalInputEvent> {
  val terminalStarter = services.terminalStarter
  val textBuffer = services.textBuffer
  val controller = services.controller
  val inputChannel = Channel<TerminalInputEvent>(capacity = Channel.UNLIMITED)

  coroutineScope.launch {
    for (event in inputChannel) {
      when (event) {
        is TerminalWriteBytesEvent -> {
          terminalStarter.sendBytes(event.bytes, false)
        }
        is TerminalResizeEvent -> {
          terminalStarter.postResize(event.newSize.toTermSize(), RequestOrigin.User)
        }
        TerminalCloseEvent -> {
          terminalStarter.close()
          terminalStarter.ttyConnector.waitFor(STOP_EMULATOR_TIMEOUT) {
            terminalStarter.requestEmulatorStop()
          }
        }
        is TerminalClearBufferEvent -> {
          textBuffer.withLock {
            textBuffer.clearHistory()
            // We need to clear the screen and keep the last line.
            // For some reason, it's necessary even if there's just one line,
            // otherwise the model ends up in a peculiar state
            // (the cursor jumps to the bottom-left corner of the empty screen).
            // But we can only keep the current line if the cursor position is valid to begin with.
            // The cursor is 1-based, the lines are 0-based (sic!),
            // so y > 0 means the cursor is valid, y - 1 stands for the line where the cursor is,
            // and in the end cursor should be on the first line, so y = 1.
            if (controller.y > 0) {
              val lastLine = textBuffer.getLine(controller.y - 1)
              textBuffer.clearScreenBuffer()
              textBuffer.addLine(lastLine)
              controller.y = 1
            }
          }
        }
      }
    }
  }

  return inputChannel
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

private fun ensureEmulationActive(starter: StopAwareTerminalStarter) {
  if (Thread.interrupted() || starter.isStopped) {
    throw CancellationException("Terminal emulation was stopped")
  }
}

private class JediTermServices(
  val textBuffer: TerminalTextBuffer,
  val terminalDisplay: TerminalDisplayImpl,
  val controller: ObservableJediTerminal,
  val executorService: TerminalExecutorServiceManager,
  val terminalStarter: StopAwareTerminalStarter,
)
