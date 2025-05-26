// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
interface TerminalOutputModelListener : EventListener {
  /**
   * Called before actual changes in the document and highlightings in [TerminalOutputModel.updateContent].
   */
  fun beforeContentChanged(model: TerminalOutputModel) {}

  /**
   * Called after changing the document and highlightings in [TerminalOutputModel.updateContent].
   * @param startOffset offset from which document was updated.
   */
  fun afterContentChanged(model: TerminalOutputModel, startOffset: Int) {}
}