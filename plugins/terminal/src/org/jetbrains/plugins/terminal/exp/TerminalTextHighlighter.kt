// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.editor.highlighter.HighlighterClient
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.psi.tree.IElementType
import com.intellij.terminal.TerminalColorPalette
import com.jediterm.terminal.TextStyle
import org.jetbrains.plugins.terminal.exp.TerminalUiUtils.toTextAttributes

data class HighlightingInfo(val startOffset: Int, val endOffset: Int, val textAttributesProvider: TextAttributesProvider) {
  init {
    check(startOffset <= endOffset)
  }
  val length: Int
    get() = endOffset - startOffset
}

internal data class TextWithHighlightings(val text: String, val highlightings: List<HighlightingInfo>)

internal data class TextWithAttributes(val text: String, val attributes: TextAttributesProvider)

interface TextAttributesProvider {
  fun getTextAttributes(): TextAttributes
}

object EmptyTextAttributesProvider : TextAttributesProvider {
  override fun getTextAttributes(): TextAttributes = TextAttributes.ERASE_MARKER
}

class TextStyleAdapter(private val style: TextStyle,
                       private val colorPalette: TerminalColorPalette): TextAttributesProvider {
  override fun getTextAttributes(): TextAttributes = style.toTextAttributes(colorPalette)
}

class TextAttributesKeyAdapter(private val editor: Editor, private val textAttributesKey: TextAttributesKey) : TextAttributesProvider {
  override fun getTextAttributes(): TextAttributes = editor.colorsScheme.getAttributes(textAttributesKey)
}

internal fun List<TextWithAttributes>.toTextWithHighlightings(): TextWithHighlightings {
  val builder = StringBuilder()
  val highlightings = mutableListOf<HighlightingInfo>()
  for (component in this) {
    val startOffset = builder.length
    builder.append(component.text)
    highlightings.add(HighlightingInfo(startOffset, builder.length, component.attributes))
  }
  return TextWithHighlightings(builder.toString(), highlightings)
}

/** Returns a new list where an [adjustmentValue] added to the start and end offsets of each highlighting */
internal fun List<HighlightingInfo>.rebase(adjustmentValue: Int): List<HighlightingInfo> {
  return map { HighlightingInfo(adjustmentValue + it.startOffset, adjustmentValue + it.endOffset, it.textAttributesProvider) }
}

class TerminalTextHighlighter private constructor(
  private val allHighlightingsSnapshotProvider: () -> AllHighlightingsSnapshot
) : EditorHighlighter {
  private var editor: HighlighterClient? = null

  constructor(model: TerminalOutputModel) : this({ model.getHighlightingsSnapshot() })
  internal constructor(allHighlightingsSnapshot: AllHighlightingsSnapshot) : this({ allHighlightingsSnapshot })

  override fun createIterator(startOffset: Int): HighlighterIterator {
    val highlightingsSnapshot = allHighlightingsSnapshotProvider()
    val curInd = highlightingsSnapshot.findHighlightingIndex(startOffset)
    return MyHighlighterIterator(editor?.document, highlightingsSnapshot, curInd)
  }

  override fun setEditor(editor: HighlighterClient) {
    this.editor = editor
  }

  private class MyHighlighterIterator(private val document: Document?,
                                      private val highlightings: AllHighlightingsSnapshot,
                                      private var curInd: Int) : HighlighterIterator {

    override fun getStart(): Int = highlightings[curInd].startOffset

    override fun getEnd(): Int = highlightings[curInd].endOffset

    override fun getTextAttributes(): TextAttributes {
      return highlightings[curInd].textAttributesProvider.getTextAttributes()
    }

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