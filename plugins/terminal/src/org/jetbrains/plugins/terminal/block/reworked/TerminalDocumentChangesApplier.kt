// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import org.jetbrains.annotations.ApiStatus

/**
 * Used to wrap terminal document modifications.
 * For example, into write action.
 */
@ApiStatus.Internal
interface TerminalDocumentChangesApplier {
  fun applyChange(action: () -> Unit)
}

@ApiStatus.Internal
class WriteActionTerminalDocumentChangesApplier : TerminalDocumentChangesApplier {
  override fun applyChange(action: () -> Unit) {
    return runWriteAction {
      CommandProcessor.getInstance().runUndoTransparentAction {
        action()
      }
    }
  }
}