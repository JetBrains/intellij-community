package com.intellij.terminal.frontend.view

import com.intellij.openapi.Disposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import java.util.*

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalTextSelectionModel {
  @get:RequiresEdt
  val selection: TerminalTextSelection?

  @RequiresEdt
  fun updateSelection(newSelection: TerminalTextSelection?)

  fun addListener(parentDisposable: Disposable, listener: TerminalTextSelectionListener)
}

@ApiStatus.Experimental
interface TerminalTextSelectionListener : EventListener {
  fun selectionChanged(event: TerminalTextSelectionChangeEvent)
}

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalTextSelectionChangeEvent {
  val outputModel: TerminalOutputModel
  val oldSelection: TerminalTextSelection?
  val newSelection: TerminalTextSelection?
}

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