// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.util.Key
import com.intellij.util.MathUtil
import org.jetbrains.plugins.terminal.exp.TerminalSelectionModel.TerminalSelectionListener
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import kotlin.math.min

class TerminalSelectionController(
  private val focusModel: TerminalFocusModel,
  private val selectionModel: TerminalSelectionModel,
  private val outputModel: TerminalOutputModel
) : EditorMouseListener, TerminalSelectionListener {
  val primarySelection: CommandBlock?
    get() = selectionModel.primarySelection

  init {
    outputModel.editor.addEditorMouseListener(this)
    selectionModel.addListener(this)
    focusModel.addPromptFocusListener(object : FocusAdapter() {
      override fun focusGained(e: FocusEvent?) {
        clearSelection()   // clear selection if user selected the prompt using the mouse
      }
    })
    outputModel.editor.selectionModel.addSelectionListener(object : SelectionListener {
      override fun selectionChanged(e: SelectionEvent) {
        if (!e.newRange.isEmpty) {
          selectionModel.selectedBlocks = emptyList()
        }
      }
    })
  }

  fun selectRelativeBlock(isBelow: Boolean) {
    val selectedBlock = selectionModel.primarySelection
    if (selectedBlock != null) {
      val curIndex = outputModel.getIndexOfBlock(selectedBlock)
      if (curIndex >= 0) {
        val newIndex = if (isBelow) curIndex + 1 else curIndex - 1
        if (newIndex in (0 until outputModel.getBlocksSize())) {
          val newBlock = outputModel.getByIndex(newIndex)
          selectionModel.selectedBlocks = listOf(newBlock)
          makeBlockVisible(newBlock)
        }
      }
    }
  }

  fun selectLastBlock() {
    val block = outputModel.getLastBlock() ?: return
    selectionModel.selectedBlocks = listOf(block)
    makeBlockVisible(block)
  }

  fun clearSelection() {
    selectionModel.selectedBlocks = emptyList()
    outputModel.editor.selectionModel.removeSelection()
    focusModel.focusPrompt()
  }

  override fun mouseClicked(event: EditorMouseEvent) {
    val block = outputModel.getByOffset(event.offset)
    if (block != null) {
      // check that click is right inside the block
      val bounds = outputModel.getBlockBounds(block)
      if (bounds.contains(event.mouseEvent.point)) {
        selectionModel.selectedBlocks = listOf(block)
        return
      }
    }
    clearSelection()
  }

  override fun selectionChanged(oldSelection: List<CommandBlock>, newSelection: List<CommandBlock>) {
    if (newSelection.isNotEmpty()) {
      focusModel.focusOutput()
    }
    else focusModel.focusPrompt()
  }

  private fun makeBlockVisible(block: CommandBlock) {
    val editor = outputModel.editor
    val bounds = outputModel.getBlockBounds(block)
    val visibleArea = editor.scrollingModel.visibleArea
    if (bounds.y !in visibleArea.y until (visibleArea.y + visibleArea.height)) {
      val scrollOffset = if (bounds.y < visibleArea.y) {
        bounds.y - TerminalUi.blocksGap
      }
      else { // make top part of the block visible
        val blockMaxHeight = min(bounds.height + TerminalUi.blocksGap, visibleArea.height)
        bounds.y + blockMaxHeight - visibleArea.height
      }
      val offset = MathUtil.clamp(scrollOffset, 0, editor.contentComponent.height)
      editor.scrollingModel.scrollVertically(offset)
    }
  }

  companion object {
    val KEY: Key<TerminalSelectionController> = Key.create("TerminalSelectionController")
  }
}