package com.intellij.terminal.frontend.view.impl

import com.intellij.openapi.editor.Editor
import kotlinx.coroutines.Deferred
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.util.getNow
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent

internal class TerminalMouseEventsHandlerImpl(
  private val editor: Editor,
  private val terminalInput: TerminalInput,
  private val session: Deferred<TerminalSession>,
) : TerminalMouseEventsHandler {
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

    val encodedEvent = terminalSession.processMouseEvent(event, x, y)
    if (encodedEvent == null) {
      // Null means that mouse reporting is not enabled now or Shift modifier is used,
      // so we leave the event to the editor logic (for example, context menu or text selection).
      return
    }

    terminalInput.sendBytes(encodedEvent)
    if (event is MouseWheelEvent) {
      editor.selectionModel.removeSelection()
      event.consume()
    }
  }
}
