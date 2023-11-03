// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.util.MathUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.terminal.exp.TerminalFocusModel.TerminalFocusListener
import org.jetbrains.plugins.terminal.exp.TerminalSelectionModel.TerminalSelectionListener
import javax.swing.SwingUtilities
import kotlin.math.min

class TerminalSelectionController(
  private val focusModel: TerminalFocusModel,
  private val selectionModel: TerminalSelectionModel,
  private val outputModel: TerminalOutputModel
) : EditorMouseListener, TerminalSelectionListener {
  val selectedBlocks: List<CommandBlock>
    get() = selectionModel.selectedBlocks

  val primarySelection: CommandBlock?
    get() = selectionModel.primarySelection

  private val textSelectionModel: SelectionModel
    get() = outputModel.editor.selectionModel

  init {
    outputModel.editor.addEditorMouseListener(this)
    selectionModel.addListener(this)
    focusModel.addListener(object : TerminalFocusListener {
      override fun promptFocused() {
        clearSelection()   // clear selection if user selected the prompt using the mouse
      }

      // mark selected blocks as inactive when the terminal loses the focus
      override fun activeStateChanged(isActive: Boolean) {
        for (block in selectedBlocks) {
          if (isActive) {
            outputModel.removeBlockState(block, InactiveSelectedBlockDecorationState.NAME)
          }
          else {
            outputModel.addBlockState(block, InactiveSelectedBlockDecorationState())
          }
        }
      }
    })
    textSelectionModel.addSelectionListener(object : SelectionListener {
      override fun selectionChanged(e: SelectionEvent) {
        if (!e.newRange.isEmpty) {
          selectionModel.selectedBlocks = emptyList()
        }
      }
    })
  }

  @RequiresEdt
  fun selectRelativeBlock(isBelow: Boolean, dropCurrentSelection: Boolean) {
    val primaryBlock = selectionModel.primarySelection
    if (primaryBlock != null) {
      val curIndex = outputModel.getIndexOfBlock(primaryBlock)
      if (curIndex >= 0) {
        val newIndex = if (isBelow) curIndex + 1 else curIndex - 1
        if (newIndex in (0 until outputModel.getBlocksSize())) {
          val newBlock = outputModel.getByIndex(newIndex)
          selectionModel.selectedBlocks = when {
            dropCurrentSelection -> listOf(newBlock)
            selectedBlocks.contains(newBlock) -> selectedBlocks - primaryBlock  // selection is decreasing
            else -> selectedBlocks + newBlock
          }
          makeBlockVisible(newBlock)
        }
        else if (isBelow) {
          // The last block is already selected, so scroll to the end of the output
          val editor = outputModel.editor
          val visibleHeight = editor.scrollingModel.visibleArea.height
          editor.scrollingModel.scrollVertically(editor.contentComponent.height - visibleHeight)
        }
      }
    }
  }

  @RequiresEdt
  fun selectLastBlock() {
    val block = outputModel.getLastBlock() ?: return
    selectionModel.selectedBlocks = listOf(block)
    makeBlockVisible(block)
  }

  @RequiresEdt
  fun clearSelection() {
    selectionModel.selectedBlocks = emptyList()
    textSelectionModel.removeSelection()
    focusModel.focusPrompt()
  }

  override fun mouseClicked(event: EditorMouseEvent) {
    if (event.mouseEvent.clickCount == 1) {
      val block = getBlockUnderMouse(event)
      if (block != null) {
        selectionModel.selectedBlocks = listOf(block)
      }
      else clearSelection()
    }
  }

  override fun mousePressed(event: EditorMouseEvent) {
    if (!SwingUtilities.isRightMouseButton(event.mouseEvent)) {
      return
    }
    val block = getBlockUnderMouse(event)
    if (block != null) {
      val insideTextSelection = textSelectionModel.let { event.offset in (it.selectionStart..it.selectionEnd) }
      if (!selectionModel.selectedBlocks.contains(block) && !insideTextSelection) {
        selectionModel.selectedBlocks = listOf(block)
      }
    }
    else clearSelection()
  }

  override fun selectionChanged(oldSelection: List<CommandBlock>, newSelection: List<CommandBlock>) {
    if (newSelection.isNotEmpty()) {
      textSelectionModel.removeSelection()
      focusModel.focusOutput()
    }
    else if (!textSelectionModel.hasSelection()) {
      focusModel.focusPrompt()
    }
  }

  private fun getBlockUnderMouse(event: EditorMouseEvent): CommandBlock? {
    val block = outputModel.getByOffset(event.offset)
    if (block != null) {
      // check that click is right inside the block
      val bounds = outputModel.getBlockBounds(block)
      if (bounds.contains(event.mouseEvent.point)) {
        return block
      }
    }
    return null
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
    val KEY: DataKey<TerminalSelectionController> = DataKey.create("TerminalSelectionController")
  }
}