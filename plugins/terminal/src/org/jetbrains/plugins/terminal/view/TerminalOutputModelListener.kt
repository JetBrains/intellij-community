// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view

import org.jetbrains.annotations.ApiStatus

/**
 * A listener for terminal output model events.
 * Any exceptions thrown by the listener are caught and logged to not break the [TerminalOutputModel] consistency.
 *
 * @see TerminalOutputModel.addListener
 */
@ApiStatus.Experimental
interface TerminalOutputModelListener {

  /**
   * Called before every change in the model content.
   *
   * Every call is always followed by [afterContentChanged] when the change is applied.
   */
  fun beforeContentChanged(model: TerminalOutputModel) {}

  /**
   * Called after every change in the model content.
   *
   * It is guaranteed that at the moment of this call the model is in a consistent state
   * (the cursor offset is valid, the highlightings are valid, and so on).
   *
   * Note that there can be no-op changes, when some text was replaced by the same text.
   * In this case, an event will still be fired,
   * but both [TerminalContentChangeEvent.oldText] and [TerminalContentChangeEvent.newText]
   * will be empty, and [TerminalContentChangeEvent.offset] will contain some value
   * between [TerminalOutputModel.startOffset] and [TerminalOutputModel.endOffset] (inclusive)
   * that is not supposed to be used.
   */
  fun afterContentChanged(event: TerminalContentChangeEvent) {}

  /**
   * Called every time the cursor is moved.
   *
   * Not called if the cursor position relative to [TerminalOutputModel.startOffset] has changed because of trimming
   * if the absolute cursor offset remained the same.
   */
  fun cursorOffsetChanged(event: TerminalCursorOffsetChangeEvent) {}
}

/**
 * The common interface for terminal output model events.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalOutputModelEvent {
  /**
   * The model that fired the event.
   */
  val model: TerminalOutputModel
}

/**
 * An event that is fired after the model has changed.
 *
 * @see TerminalOutputModelListener.afterContentChanged
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalContentChangeEvent : TerminalOutputModelEvent {

  /**
   * The offset containing the first changed character.
   *
   * If the change is empty, this value should not be used,
   * but it's guaranteed to be between [TerminalOutputModel.startOffset] and [TerminalOutputModel.endOffset] (inclusive).
   *
   * If [isTrimming] is `true`, then the offset will always be less than [TerminalOutputModel.startOffset],
   * otherwise it'll be between [TerminalOutputModel.startOffset] and [TerminalOutputModel.endOffset] (inclusive).
   */
  val offset: TerminalOffset

  /**
   * The text before the change.
   *
   * See [TerminalOutputModel.getText] for explanation why it's a [CharSequence] and not a [String].
   */
  val oldText: CharSequence

  /**
   * The text after the change.
   *
   * See [TerminalOutputModel.getText] for explanation why it's a [CharSequence] and not a [String].
   */
  val newText: CharSequence

  /**
   * If `true`, means that the change was caused by the user typing and not by the shell process output.
   *
   * Such changes are temporary: they're based on output prediction, and will eventually be overwritten
   * by the real output if it's different from the prediction.
   */
  val isTypeAhead: Boolean

  /**
   * If `true`, means that this change is a removal from the start of the output.
   *
   * The removed range is between [offset] and [TerminalOutputModel.startOffset].
   * The removed text is [oldText]. The [newText] value is always empty.
   */
  val isTrimming: Boolean
}

/**
 * A cursor position change event.
 *
 * @see TerminalOutputModelListener.cursorOffsetChanged
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalCursorOffsetChangeEvent : TerminalOutputModelEvent {
  /**
   * The old cursor offset value.
   */
  val oldOffset: TerminalOffset

  /**
   * The new cursor offset value.
   */
  val newOffset: TerminalOffset
}
