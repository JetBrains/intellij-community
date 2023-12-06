// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.editor.highlighter.HighlighterClient
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.psi.tree.IElementType
import java.util.*

data class HighlightingInfo(val startOffset: Int, val endOffset: Int, val textAttributes: TextAttributes)

class TerminalTextHighlighter private constructor(private val getHighlightings: () -> List<HighlightingInfo>) : EditorHighlighter {
  private var editor: HighlighterClient? = null

  constructor(model: TerminalOutputModel) : this({ model.getAllHighlightings() })
  constructor(highlightings: List<HighlightingInfo>) : this({ highlightings })

  override fun createIterator(startOffset: Int): HighlighterIterator {
    val highlightings = getHighlightings()
    val curInd = findOffsetIndex(highlightings, startOffset)
    return MyHighlighterIterator(editor?.document, highlightings, curInd)
  }

  private fun findOffsetIndex(highlightings: List<HighlightingInfo>, offset: Int): Int {
    if (offset < 0) return 0
    val binarySearchInd = Collections.binarySearch(highlightings, HighlightingInfo(offset, offset, TextAttributes.ERASE_MARKER)) { a, b ->
      a.startOffset.compareTo(b.startOffset)
    }
    return if (binarySearchInd >= 0) binarySearchInd
    else {
      val insertionIndex = -binarySearchInd - 1
      (insertionIndex - 1).coerceAtLeast(0)
    }
  }

  override fun setEditor(editor: HighlighterClient) {
    this.editor = editor
  }

  private class MyHighlighterIterator(private val document: Document?,
                                      private val highlightings: List<HighlightingInfo>,
                                      private var curInd: Int) : HighlighterIterator {

    override fun getStart(): Int = highlightings[curInd].startOffset

    override fun getEnd(): Int = highlightings[curInd].endOffset

    override fun getTextAttributes(): TextAttributes = highlightings[curInd].textAttributes

    override fun getTokenType(): IElementType? = null

    override fun advance() {
      if (curInd < highlightings.size) curInd++
    }

    override fun retreat() {
      if (curInd > -1) curInd--
    }

    override fun atEnd(): Boolean = curInd < 0 || curInd >= highlightings.size

    override fun getDocument(): Document? = document
  }
}