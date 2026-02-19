package com.intellij.terminal.frontend.view

import com.intellij.openapi.Disposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModel

/**
 * The model that manages the state of text selection in both output models
 * of [org.jetbrains.plugins.terminal.view.TerminalOutputModelsSet].
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalTextSelectionModel {
  /**
   * Current selection range.
   * Offsets are actual for the currently active output model ([org.jetbrains.plugins.terminal.view.TerminalOutputModelsSet.active]).
   *
   * Null value means that there is no selection.
   */
  @get:RequiresEdt
  val selection: TerminalTextSelection?

  /**
   * Updates the selection range in the currently active output model
   * ([org.jetbrains.plugins.terminal.view.TerminalOutputModelsSet.active]).
   */
  @RequiresEdt
  fun updateSelection(newSelection: TerminalTextSelection?)

  fun addListener(parentDisposable: Disposable, listener: TerminalTextSelectionListener)
}

@ApiStatus.Experimental
interface TerminalTextSelectionListener {
  /**
   * Called when the selection range in one of the [TerminalOutputModel]'s is changed.
   */
  fun selectionChanged(event: TerminalTextSelectionChangeEvent)
}

/**
 * The description of the selection range change happened in the [outputModel].
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalTextSelectionChangeEvent {
  val outputModel: TerminalOutputModel

  /** Null value means that there was no selection before the change */
  val oldSelection: TerminalTextSelection?

  /** Null value means that there is no selection after the change */
  val newSelection: TerminalTextSelection?
}

/**
 * Represents a text selection range in one of the [TerminalOutputModel]'s.
 * It is always non-empty.
 */
@ApiStatus.Experimental
sealed interface TerminalTextSelection {
  val startOffset: TerminalOffset
  val endOffset: TerminalOffset

  companion object {
    fun of(startOffset: TerminalOffset, endOffset: TerminalOffset): TerminalTextSelection {
      require(startOffset < endOffset) { "startOffset must be less than endOffset and selection should not be empty" }
      return TerminalTextSelectionImpl(startOffset, endOffset)
    }
  }
}

private data class TerminalTextSelectionImpl(
  override val startOffset: TerminalOffset,
  override val endOffset: TerminalOffset,
) : TerminalTextSelection