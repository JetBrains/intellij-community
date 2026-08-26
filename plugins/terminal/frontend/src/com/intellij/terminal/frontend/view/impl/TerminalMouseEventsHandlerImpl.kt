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

    val encodedEvent = if (event is MouseWheelEvent) {
      processWheelEvent(event, x, y, terminalSession)
    }
    else {
      terminalSession.processMouseEvent(event, x, y)
    }
    if (encodedEvent == null) {
      // Null means that mouse reporting is not enabled now or Shift modifier is used,
      // so we leave the event to the editor logic (for example, context menu or text selection).
      return
    }

    terminalInput.sendBytes(encodedEvent)
    if (event is MouseWheelEvent) {
      editor.selectionModel.removeSelection(true)
    }
    if (event.id == MouseEvent.MOUSE_PRESSED) {
      // Editor selection can be active at this moment only if the user holds Shift.
      // Support the case of removing the selection here, because editor logic won't be able to do it
      // (we consume the event).
      editor.selectionModel.removeSelection(true)
    }

    // Consume the mouse event to avoid double processing:
    // by the terminal process and the editor logic (for example, text selection).
    event.consume()
  }

  /**
   * Normalizes [event]'s wheel delta into whole terminal rows via [wheelAccumulator] before asking [terminalSession] to encode anything.
   * Replays single-unit encoded bytes `abs(lines)` times, once per normalized row, instead of trusting the raw event's own magnitude.
   *
   * Returns `null` if the terminal session didn't handle the event (mouse wheel events shouldn't be reported in the current state).
   *
   * TODO: current approach of creating fake single-unit scroll events and encoding them is hacky.
   *  Probably we need to rethink responsibility distribution between this class and processing inside [TerminalSession.processMouseEvent].
   *  Like, to delegate only encoding to [TerminalSession.processMouseEvent].
   */
  private fun processWheelEvent(event: MouseWheelEvent, x: Int, y: Int, terminalSession: TerminalSession): ByteArray? {
    val lines = wheelAccumulator.consumeLines(event)
    if (lines == 0) return null

    val linesToScroll = abs(lines)
    val sign = if (lines > 0) 1 else -1
    // We assume that `processMouseEvent` will return the same byte array for the same event and has no side effects.
    val singleScrollBytes = terminalSession.processMouseEvent(synthesizeUnitWheelEvent(event, sign), x, y)
    return when {
      singleScrollBytes == null -> null
      linesToScroll == 1 -> singleScrollBytes
      else -> {
        ByteArray(linesToScroll * singleScrollBytes.size) {
          singleScrollBytes[it % singleScrollBytes.size]
        }
      }
    }
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
}
