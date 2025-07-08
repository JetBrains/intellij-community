// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.output

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
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.reworked.lang.TerminalOutputTokenTypes
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils.toTextAttributes

@ApiStatus.Internal
data class HighlightingInfo(val startOffset: Int, val endOffset: Int, val textAttributesProvider: TextAttributesProvider) {
  init {
    check(startOffset <= endOffset)
  }
  val length: Int
    get() = endOffset - startOffset
}

internal data class TextWithHighlightings(val text: String, val highlightings: List<HighlightingInfo>)

internal data class TextWithAttributes(val text: String, val attributes: TextAttributesProvider)

@ApiStatus.Internal
interface TextAttributesProvider {
  fun getTextAttributes(): TextAttributes
}

internal object EmptyTextAttributesProvider : TextAttributesProvider {
  override fun getTextAttributes(): TextAttributes = TextAttributes.ERASE_MARKER
}

internal class TextStyleAdapter(private val style: TextStyle,
                       private val colorPalette: TerminalColorPalette): TextAttributesProvider {
  override fun getTextAttributes(): TextAttributes = style.toTextAttributes(colorPalette)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as TextStyleAdapter

    return style == other.style
  }

  override fun hashCode(): Int {
    return style.hashCode()
  }

  override fun toString(): String {
    return "TextStyleAdapter(style=TextStyle(fg=${style.foreground}, bg=${style.background}, op=${TextStyle.Option.entries.filter { style.hasOption(it) }}))"
  }
}

internal class TextAttributesKeyAdapter(private val editor: Editor, private val textAttributesKey: TextAttributesKey) : TextAttributesProvider {
  override fun getTextAttributes(): TextAttributes = editor.colorsScheme.getAttributes(textAttributesKey)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as TextAttributesKeyAdapter

    return textAttributesKey == other.textAttributesKey
  }

  override fun hashCode(): Int {
    return textAttributesKey.hashCode()
  }

  override fun toString(): String {
    return "TextAttributesKeyAdapter(textAttributesKey=$textAttributesKey)"
  }
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

@ApiStatus.Internal
class TerminalTextHighlighter(
  private val highlightingsSnapshotProvider: () -> TerminalOutputHighlightingsSnapshot,
) : EditorHighlighter {
  private var editor: HighlighterClient? = null

  constructor(model: TerminalOutputModel) : this({ model.getHighlightingsSnapshot() })
  constructor(highlightingsSnapshot: TerminalOutputHighlightingsSnapshot) : this({ highlightingsSnapshot })

  override fun createIterator(startOffset: Int): HighlighterIterator {
    val highlightingsSnapshot = highlightingsSnapshotProvider()
    val curInd = highlightingsSnapshot.findHighlightingIndex(startOffset)
    return MyHighlighterIterator(editor?.document, highlightingsSnapshot, curInd)
  }

  override fun setEditor(editor: HighlighterClient) {
    this.editor = editor
  }

  private class MyHighlighterIterator(private val document: Document?,
                                      private val highlightings: TerminalOutputHighlightingsSnapshot,
                                      private var curInd: Int) : HighlighterIterator {

    override fun getStart(): Int = highlightings[curInd].startOffset

    override fun getEnd(): Int = highlightings[curInd].endOffset

    override fun getTextAttributes(): TextAttributes {
      return highlightings[curInd].textAttributesProvider.getTextAttributes()
    }

    override fun getTokenType(): IElementType = TerminalOutputTokenTypes.TEXT

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
