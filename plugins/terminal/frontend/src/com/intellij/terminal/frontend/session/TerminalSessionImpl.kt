package com.intellij.terminal.frontend.session

import com.intellij.platform.eel.EelDescriptor
import com.jediterm.core.input.KeyInputEvent
import com.jediterm.terminal.emulator.keyboard.KeyEventProcessingSettings
import com.jediterm.terminal.emulator.keyboard.TerminalKeyEventProcessor
import com.jediterm.terminal.emulator.mouse.MouseEventProcessingSettings
import com.jediterm.terminal.emulator.mouse.TerminalMouseEventEncoder
import com.jediterm.terminal.ui.input.AwtMouseEvent
import com.jediterm.terminal.ui.input.AwtMouseWheelEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.LocalTerminalTtyConnector
import org.jetbrains.plugins.terminal.TerminalUtil
import org.jetbrains.plugins.terminal.session.impl.TerminalInputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalOutputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.session.impl.TerminalSessionTerminatedEvent
import org.jetbrains.plugins.terminal.session.impl.dto.KeyEventProcessingResultDto
import org.jetbrains.plugins.terminal.session.impl.dto.toDto
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent

internal class TerminalSessionImpl(
  private val inputChannel: SendChannel<TerminalInputEvent>,
  outputFlow: Flow<List<TerminalOutputEvent>>,
  override val coroutineScope: CoroutineScope,
  private val ttyConnector: LocalTerminalTtyConnector,
  private val terminalDisplay: TerminalDisplayImpl,
  private val terminal: ObservableJediTerminal,
) : TerminalSession {
  private val myMouseEventEncoder: TerminalMouseEventEncoder = TerminalMouseEventEncoder()

  @Volatile
  override var isClosed: Boolean = false
    private set

  private val closeAwareOutputFlow = outputFlow.onEach { events ->
    if (events.any { it == TerminalSessionTerminatedEvent }) {
      isClosed = true
    }
  }

  override val eelDescriptor: EelDescriptor
    get() = ttyConnector.eelDescriptor

  override val processId: Long
    get() = ttyConnector.shellEelProcess.eelProcess.pid.value

  override suspend fun getInputChannel(): SendChannel<TerminalInputEvent> {
    if (isClosed) {
      return Channel<TerminalInputEvent>(capacity = 0).also { it.close() }
    }

    return inputChannel
  }

  override suspend fun getOutputFlow(): Flow<List<TerminalOutputEvent>> {
    if (isClosed) {
      return emptyFlow()
    }

    return closeAwareOutputFlow
  }

  override suspend fun hasRunningCommands(): Boolean {
    if (isClosed) return false
    return withContext(Dispatchers.IO) {
      TerminalUtil.hasRunningCommands(ttyConnector)
    }
  }

  override fun processMouseEvent(
    e: MouseEvent,
    x: Int,
    y: Int,
  ): ByteArray? {
    val mouseEvent = if (e is MouseWheelEvent) AwtMouseWheelEvent(e) else AwtMouseEvent(e)
    return myMouseEventEncoder.encode(
      mouseEvent,
      x,
      y,
      terminalDisplay.mouseMode,
      terminalDisplay.mouseFormat,
      terminal,
      createMouseEventSettings()
    )
  }

  private fun createMouseEventSettings(): MouseEventProcessingSettings {
    return MouseEventProcessingSettings(
      terminalDisplay.settings.enableMouseReporting(),
      terminal.alternativeBufferEnabled,
      terminalDisplay.settings.simulateMouseScrollWithArrowKeysInAlternativeScreen(),
    )
  }

  override fun processKeyEvent(e: KeyEvent): KeyEventProcessingResultDto {
    val type = when (e.id) {
      KeyEvent.KEY_PRESSED -> KeyInputEvent.Type.PRESSED
      KeyEvent.KEY_TYPED -> KeyInputEvent.Type.TYPED
      else -> return KeyEventProcessingResultDto.Unhandled
    }

    val event = KeyInputEvent(
      type = type,
      keyCode = e.keyCode,
      keyChar = e.keyChar,
      modifiersEx = e.modifiersEx,
    )

    val settings = KeyEventProcessingSettings(
      shiftEnterSendsEscCR = terminalDisplay.settings.shiftEnterSendsEscCR(),
      scrollToBottomOnTyping = terminalDisplay.settings.scrollToBottomOnTyping(),
      altSendsEscape = terminalDisplay.settings.altSendsEscape(),
    )
    return TerminalKeyEventProcessor.processKey(event, terminal, settings).toDto()
  }
}
