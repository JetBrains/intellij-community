// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.TextRange

class TerminalOutputModel(editor: EditorEx) {
  private val blocks: MutableList<CommandBlock> = mutableListOf()

  private val document: Document = editor.document

  fun addBlock(command: String?) {
    val textLength = document.textLength
    val startOffset = if (textLength != 0) {
      document.insertString(textLength, "\n")
      textLength + 1
    }
    else 0
    val marker = document.createRangeMarker(startOffset, startOffset)
    marker.isGreedyToRight = true
    blocks.add(CommandBlock(command, marker))
    addDecorations(marker)
  }

  fun removeBlock(block: CommandBlock) {
    document.deleteString(block.startOffset, block.endOffset)
    block.dispose()
    blocks.remove(block)
  }

  fun getLastBlock(): CommandBlock? {
    return blocks.lastOrNull()
  }

  private fun addDecorations(range: RangeMarker) {
    // todo: add inlays and highlighters to create visual block
  }
}

data class CommandBlock(val command: String?, private val range: RangeMarker) {
  val startOffset: Int
    get() = range.startOffset
  val endOffset: Int
    get() = range.endOffset
  val textRange: TextRange
    get() = range.textRange

  fun dispose() {
    range.dispose()
  }
}