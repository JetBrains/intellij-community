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
    val terminalSession = session.getNow() ?: return
    val encodedEvent = terminalSession.processMouseEvent(
      event,
      x,
      y,
    ) ?: return
    terminalInput.sendBytes(encodedEvent)
    if (event is MouseWheelEvent) {
      editor.selectionModel.removeSelection()
      event.consume()
    }
  }
}
