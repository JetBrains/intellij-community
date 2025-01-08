// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.TerminalExecutorServiceManagerImpl
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
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.block.reworked.session.output.ObservableJediTerminal
import org.jetbrains.plugins.terminal.block.reworked.session.output.TerminalDisplayImpl
import org.jetbrains.plugins.terminal.block.reworked.session.output.createTerminalOutputChannel
import org.jetbrains.plugins.terminal.util.STOP_EMULATOR_TIMEOUT
import org.jetbrains.plugins.terminal.util.waitFor

private val LOG: Logger = Logger.getInstance(TerminalSession::class.java)

internal fun startTerminalSession(
  connector: TtyConnector,
  termSize: TermSize,
  settings: JBTerminalSystemSettingsProviderBase,
  coroutineScope: CoroutineScope,
): TerminalSession {
  val maxHistoryLinesCount = AdvancedSettings.getInt("terminal.buffer.max.lines.count")
  val services: JediTermServices = createJediTermServices(connector, termSize, maxHistoryLinesCount, settings)

  val outputScope = coroutineScope.childScope("Terminal output forwarding")
  val outputChannel = createTerminalOutputChannel(services.textBuffer, services.terminalDisplay, services.controller, outputScope)

  val inputScope = coroutineScope.childScope("Terminal input handling")
  val inputChannel = createTerminalInputChannel(services.terminalStarter, inputScope)

  services.executorService.unboundedExecutorService.submit {
    try {
      startTerminalEmulation(services.terminalStarter)
    }
    finally {
      coroutineScope.cancel()
    }
  }

  coroutineScope.coroutineContext.job.invokeOnCompletion {
    val starter = services.terminalStarter
    starter.close() // close in background
    //starter.ttyConnector.waitFor(STOP_EMULATOR_TIMEOUT) {
    //  starter.requestEmulatorStop()
    //}
  }

  return TerminalSessionImpl(inputChannel, outputChannel)
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
  val terminalStarter = TerminalStarter(
    controller,
    connector,
    TtyBasedArrayDataStream(connector),
    typeAheadManager,
    executorService
  )

  return JediTermServices(textBuffer, terminalDisplay, controller, executorService, terminalStarter)
}

private fun createTerminalInputChannel(
  terminalStarter: TerminalStarter,
  coroutineScope: CoroutineScope,
): SendChannel<TerminalInputEvent> {
  val inputChannel = Channel<TerminalInputEvent>(capacity = Channel.UNLIMITED)

  coroutineScope.launch {
    for (event in inputChannel) {
      when (event) {
        is TerminalWriteBytesEvent -> {
          terminalStarter.sendBytes(event.bytes, false)
        }
        is TerminalResizeEvent -> {
          terminalStarter.postResize(event.newSize, RequestOrigin.User)
        }
        is TerminalCloseEvent -> {
          terminalStarter.close()
          terminalStarter.ttyConnector.waitFor(STOP_EMULATOR_TIMEOUT) {
            terminalStarter.requestEmulatorStop()
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

private class JediTermServices(
  val textBuffer: TerminalTextBuffer,
  val terminalDisplay: TerminalDisplayImpl,
  val controller: ObservableJediTerminal,
  val executorService: TerminalExecutorServiceManager,
  val terminalStarter: TerminalStarter,
)
