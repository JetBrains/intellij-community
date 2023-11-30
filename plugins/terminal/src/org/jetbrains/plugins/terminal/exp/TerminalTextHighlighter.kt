// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.editor.highlighter.HighlighterClient
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.psi.tree.IElementType

data class HighlightingInfo(val startOffset: Int, val endOffset: Int, val textAttributes: TextAttributes)

class TerminalTextHighlighter private constructor(private val getHighlightings: () -> List<HighlightingInfo>) : EditorHighlighter {
  private var editor: HighlighterClient? = null

  constructor(model: TerminalOutputModel) : this({ model.getAllHighlightings() })
  constructor(highlightings: List<HighlightingInfo>) : this({ highlightings })

  override fun createIterator(startOffset: Int): HighlighterIterator {
    return MyHighlighterIterator(editor?.document, getHighlightings())
  }

  override fun setEditor(editor: HighlighterClient) {
    this.editor = editor
  }

  private class MyHighlighterIterator(private val document: Document?,
                                      private val highlightings: List<HighlightingInfo>) : HighlighterIterator {
    private var curInd = 0

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