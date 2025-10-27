// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
interface TerminalOutputModelListener : EventListener {
  /**
   * Called before actual changes in the document and highlightings in [MutableTerminalOutputModel.updateContent].
   */
  fun beforeContentChanged(model: TerminalOutputModel) {}

  fun afterContentChanged(event: TerminalContentChangeEvent) {}

  fun cursorOffsetChanged(event: TerminalCursorOffsetChangeEvent) {}
}

@ApiStatus.Experimental
sealed interface TerminalOutputModelEvent {
  val model: TerminalOutputModel
}

@ApiStatus.Experimental
sealed interface TerminalContentChangeEvent : TerminalOutputModelEvent {
  val offset: TerminalOffset
  val oldText: CharSequence
  val newText: CharSequence
  val isTypeAhead: Boolean
  val isTrimming: Boolean
}

@ApiStatus.Experimental
sealed interface TerminalCursorOffsetChangeEvent : TerminalOutputModelEvent {
  val oldOffset: TerminalOffset
  val newOffset: TerminalOffset
}
