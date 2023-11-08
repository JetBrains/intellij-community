// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import java.awt.event.KeyEvent

class BlockTerminalEventsHandler(
  session: TerminalSession,
  settings: JBTerminalSystemSettingsProviderBase,
  private val outputModel: TerminalOutputModel,
  private val selectionModel: TerminalSelectionModel
) : SimpleTerminalEventsHandler(session, settings) {
  override fun keyTyped(e: KeyEvent) {
    // Clear the block selection on typing
    if (selectionModel.primarySelection != null) {
      selectionModel.selectedBlocks = emptyList()
    }
    super.keyTyped(e)
  }

  override fun keyPressed(e: KeyEvent) {
    val selectedBlock = selectionModel.primarySelection
    // Send key pressed events only when no block is selected or last block is selected.
    // So, it is possible to invoke actions on blocks
    if (selectedBlock == null || selectedBlock == outputModel.getLastBlock()) {
      super.keyPressed(e)
    }
  }
}