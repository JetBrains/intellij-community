package com.intellij.terminal.frontend.view

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.view.TerminalOffset
import java.awt.event.KeyEvent

/**
 * @see TerminalView.keyEventsFlow
 */
@ApiStatus.Experimental
sealed interface TerminalKeyEvent {
  /**
   * The ID of the event is either [KeyEvent.KEY_PRESSED] or [KeyEvent.KEY_TYPED].
   */
  val awtEvent: KeyEvent

  /**
   * Offset of the cursor at the moment of the typing.
   * Relates to the currently [active][org.jetbrains.plugins.terminal.view.TerminalOutputModelsSet.active] output model.
   */
  val cursorOffset: TerminalOffset
}

internal data class TerminalKeyEventImpl(
  override val awtEvent: KeyEvent,
  override val cursorOffset: TerminalOffset,
) : TerminalKeyEvent