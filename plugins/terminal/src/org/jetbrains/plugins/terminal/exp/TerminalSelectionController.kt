// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.ex.EditorEx

class TerminalSelectionController(private val selectionModel: TerminalSelectionModel,
                                  private val outputModel: TerminalOutputModel,
                                  editor: EditorEx) : EditorMouseListener {
  init {
    editor.addEditorMouseListener(this)
  }

  override fun mouseClicked(event: EditorMouseEvent) {
    val block = outputModel.getByOffset(event.offset)
    val selection = if (block != null) {
      // check that click is right inside the block
      val bounds = outputModel.getBlockBounds(block)
      if (bounds.contains(event.mouseEvent.point)) listOf(block) else emptyList()
    }
    else emptyList()
    selectionModel.selectedBlocks = selection
  }
}