package com.intellij.terminal.frontend.view

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
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
   * Relates to [outputModel].
   */
  val cursorOffset: TerminalOffset

  /**
   * The output model of the buffer that received the event:
   * [regular][org.jetbrains.plugins.terminal.view.TerminalOutputModelsSet.regular] or
   * [alternative][org.jetbrains.plugins.terminal.view.TerminalOutputModelsSet.alternative].
   */
  val outputModel: TerminalOutputModel
}

@ApiStatus.Internal
@VisibleForTesting
data class TerminalKeyEventImpl(
  override val awtEvent: KeyEvent,
  override val cursorOffset: TerminalOffset,
  override val outputModel: TerminalOutputModel,
) : TerminalKeyEvent
