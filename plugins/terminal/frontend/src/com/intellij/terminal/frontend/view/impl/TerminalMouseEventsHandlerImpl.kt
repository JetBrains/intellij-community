package com.intellij.terminal.frontend.view.impl

import com.intellij.openapi.editor.Editor
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Deferred
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.util.getNow
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import kotlin.math.abs

internal class TerminalMouseEventsHandlerImpl(
  private val editor: Editor,
  private val terminalInput: TerminalInput,
  private val session: Deferred<TerminalSession>,
) : TerminalMouseEventsHandler {
  private val wheelAccumulator = TerminalWheelScrollAccumulator(editor)

  override fun onMouseEvent(x: Int, y: Int, event: MouseEvent) {
    if (event.isConsumed) {
      // Some other handler already consumed this event, for example, hyperlinks logic.
      // Do not send a mouse report to the process in this case.
      return
    }

    val terminalSession = session.getNow()
    if (terminalSession == null) {
      // Session is not yet available, so we can just ignore the event because there is no terminal process to send it to.
      return
    }

    val processingResult = if (event is MouseWheelEvent) {
      processWheelEvent(event, x, y, terminalSession)
    }
    else {
      processMouseEvent(event, x, y, terminalSession)
    }

    if (processingResult.bytes != null) {
      terminalInput.sendBytes(processingResult.bytes)
      if (event is MouseWheelEvent) {
        editor.selectionModel.removeSelection(true)
      }
      if (event.id == MouseEvent.MOUSE_PRESSED) {
        // Editor selection can be active at this moment only if the user holds Shift.
        // Support the case of removing the selection here, because editor logic won't be able to do it
        // (we consume the event).
        editor.selectionModel.removeSelection(true)
      }
    }

    // Consume the mouse event to avoid double processing:
    // by the terminal process and the editor logic (for example, text selection).
    if (processingResult.shouldConsume) {
      event.consume()
    }
  }

  private fun processMouseEvent(event: MouseEvent, x: Int, y: Int, terminalSession: TerminalSession): EventProcessingResult {
    val bytes = terminalSession.processMouseEvent(event, x, y)
    return EventProcessingResult(bytes, bytes != null)
  }

  /**
   * Normalizes [event]'s wheel delta into whole terminal rows via [wheelAccumulator] before asking [terminalSession] to encode anything.
   * Replays single-unit encoded bytes `abs(lines)` times, once per normalized row, instead of trusting the raw event's own magnitude.
   *
   * TODO: current approach of creating fake single-unit scroll events and encoding them is hacky.
   *  Probably we need to rethink responsibility distribution between this class and processing inside [TerminalSession.processMouseEvent].
   *  Like, to delegate only encoding to [TerminalSession.processMouseEvent].
   */
  private fun processWheelEvent(event: MouseWheelEvent, x: Int, y: Int, terminalSession: TerminalSession): EventProcessingResult {
    val lines = wheelAccumulator.consumeLines(event)
    if (lines == 0) {
      val bytes = terminalSession.processMouseEvent(synthesizeUnitWheelEvent(event, 1), x, y)
      // Report no bytes but consume the mouse event to avoid double processing in the editor logic.
      return EventProcessingResult(null, shouldConsume = bytes != null)
    }

    val linesToScroll = abs(lines)
    val sign = if (lines > 0) 1 else -1
    // We assume that `processMouseEvent` will return the same byte array for the same event and has no side effects.
    val singleScrollBytes = terminalSession.processMouseEvent(synthesizeUnitWheelEvent(event, sign), x, y)
    val allBytes = when {
      singleScrollBytes == null -> null
      linesToScroll == 1 -> singleScrollBytes
      else -> {
        ByteArray(linesToScroll * singleScrollBytes.size) {
          singleScrollBytes[it % singleScrollBytes.size]
        }
      }
    }
    return EventProcessingResult(allBytes, shouldConsume = allBytes != null)
  }

  /** A single-notch, single-direction clone of [source], with scroll amount equal to 1 and direction of [sign]. */
  private fun synthesizeUnitWheelEvent(source: MouseWheelEvent, sign: Int): MouseWheelEvent {
    return MouseWheelEvent(
      source.component,
      source.id,
      source.getWhen(),
      UIUtil.getAllModifiers(source),
      source.x,
      source.y,
      source.clickCount,
      source.isPopupTrigger,
      MouseWheelEvent.WHEEL_UNIT_SCROLL,
      1,
      sign,
    )
  }

  /**
   * @param bytes to report to the terminal process to emulate the mouse event.
   * `null` if there is nothing to report as a result of the mouse event.
   * @param shouldConsume whether to consume the mouse event.
   * Can be `true` even if [bytes] is `null`.
   * It means that the event was processed by the terminal, but there is nothing yet to report to the process.
   */
  private class EventProcessingResult(
    val bytes: ByteArray?,
    val shouldConsume: Boolean,
  )
}
