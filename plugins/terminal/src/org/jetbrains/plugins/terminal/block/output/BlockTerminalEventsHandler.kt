// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.output

import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import org.jetbrains.plugins.terminal.block.SimpleTerminalEventsHandler
import org.jetbrains.plugins.terminal.block.session.BlockTerminalSession
import java.awt.event.KeyEvent

/**
 * Block Terminal Keyboard And Mouse Events Handler.
 * Reuses most parts of `SimpleTerminalEventsHandler`.
 */
internal class BlockTerminalEventsHandler(
  session: BlockTerminalSession,
  settings: JBTerminalSystemSettingsProviderBase,
  private val outputController: TerminalOutputController
) : SimpleTerminalEventsHandler(session, settings, outputController.outputModel) {

  override fun keyTyped(e: KeyEvent) {
    // Clear the block selection on typing
    val selectionModel = outputController.selectionModel
    if (selectionModel.primarySelection != null) {
      selectionModel.selectedBlocks = emptyList()
    }
    outputController.scrollToBottom()
    super.keyTyped(e)
  }

  override fun keyPressed(e: KeyEvent) {
    val selectedBlock = outputController.selectionModel.primarySelection
    // Send key pressed events only when no block is selected or last block is selected.
    // So, it is possible to invoke actions on blocks
    if (selectedBlock == null || selectedBlock == outputModel.getActiveBlock()) {
      super.keyPressed(e)
    }
  }

}
