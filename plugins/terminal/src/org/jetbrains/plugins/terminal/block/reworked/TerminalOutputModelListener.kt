// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import java.util.*

internal interface TerminalOutputModelListener : EventListener {
  /**
   * Called before actual changes in the document and highlightings in [TerminalOutputModel.updateContent].
   */
  fun beforeContentChanged() {}

  /**
   * Called after changing the document and highlightings in [TerminalOutputModel.updateContent].
   * @param startOffset offset from which document was updated.
   */
  fun afterContentChanged(startOffset: Int) {}
}