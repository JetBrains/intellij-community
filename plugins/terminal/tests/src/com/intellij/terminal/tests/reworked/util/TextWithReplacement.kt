// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.util

import com.intellij.terminal.session.StyleRange
import com.intellij.terminal.tests.reworked.util.TextWithReplacement.Companion.STYLE1
import com.intellij.terminal.tests.reworked.util.TextWithReplacement.Companion.STYLE2
import com.intellij.terminal.tests.reworked.util.TextWithReplacement.Companion.STYLE3
import com.intellij.terminal.tests.reworked.util.TextWithReplacement.Companion.STYLE4
import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TextStyle
import org.assertj.core.api.Assertions.assertThat


internal fun parseTextWithReplacement(textWithReplacement: String): TextWithReplacement {
  val originalText = StringBuilder()
  val originalStyles = mutableListOf<StyleRange>()
  val modifiedText = StringBuilder()
  val modifiedStyles = mutableListOf<StyleRange>()
  val insertedText = StringBuilder()
  val insertedStyles = mutableListOf<StyleRange>()
  val styleIndexText = StringBuilder()
  val part = ArrayDeque(listOf(Part.UNAFFECTED))
  var removedIndex = 0
  var removedLength = 0
  fun newStyle(i: Int) = StyleRange(i.toLong(), i.toLong(), TextStyle())
  var firstSplitPartIndex: Int? = null
  for (c in textWithReplacement) {
    when (c) {
      '[' -> {
        when (part.last()) {
          Part.UNAFFECTED -> {
            originalStyles += newStyle(originalText.length)
            modifiedStyles += newStyle(modifiedText.length)
          }
          Part.REMOVED -> {
            originalStyles += newStyle(originalText.length)
          }
          Part.INSERTED -> {
            modifiedStyles += newStyle(modifiedText.length)
            insertedStyles += newStyle(insertedText.length)
          }
          Part.STYLE_INDEX -> throw IllegalArgumentException(textWithReplacement)
        }
      }
      ']' -> {
        when (part.last()) {
          Part.UNAFFECTED -> {
            originalStyles.updateLast { it.copy(endOffset = originalText.length.toLong()) }
            modifiedStyles.updateLast { it.copy(endOffset = modifiedText.length.toLong()) }
          }
          Part.REMOVED -> {
            originalStyles.updateLast { it.copy(endOffset = originalText.length.toLong()) }
            if (modifiedStyles.lastOrNull()?.startOffset == originalStyles.last().startOffset) {
              // The range is only partially removed, so the modified text will contain a part of it.
              modifiedStyles.updateLast { it.copy(endOffset = modifiedText.length.toLong()) }
            }
          }
          Part.INSERTED -> {
            modifiedStyles.updateLast { it.copy(endOffset = modifiedText.length.toLong()) }
            insertedStyles.updateLast { it.copy(endOffset = insertedText.length.toLong()) }
          }
          Part.STYLE_INDEX -> throw IllegalArgumentException(textWithReplacement)
        }
      }
      '(' -> {
        part.addLast(Part.STYLE_INDEX)
      }
      ')' -> {
        part.removeLast()
        val styleIndex = styleIndexText.toString().toInt()
        styleIndexText.clear()
        val style = when (styleIndex) {
          1 -> STYLE1
          2 -> STYLE2
          3 -> STYLE3
          4 -> STYLE4
          else -> throw IllegalArgumentException(textWithReplacement)
        }
        when (part.last()) {
          Part.UNAFFECTED -> {
            originalStyles.updateLast { it.copy(style = style) }
            modifiedStyles.updateLast { it.copy(style = style) }
            if (firstSplitPartIndex != null) {
              // If there was a split range, we need to update the style for both parts.
              modifiedStyles[firstSplitPartIndex] = modifiedStyles[firstSplitPartIndex].copy(style = style)
              firstSplitPartIndex = null
            }
          }
          Part.REMOVED -> {
            originalStyles.updateLast { it.copy(style = style) }
            if (modifiedStyles.lastOrNull()?.startOffset == originalStyles.last().startOffset) {
              // The range is only partially removed, so the modified text will contain a part of it.
              modifiedStyles.updateLast { it.copy(style = style) }
            }
          }
          Part.INSERTED -> {
            modifiedStyles.updateLast { it.copy(style = style) }
            insertedStyles.updateLast { it.copy(style = style) }
          }
          Part.STYLE_INDEX -> throw IllegalArgumentException(textWithReplacement)
        }
      }
      '<' -> {
        part.addLast(Part.REMOVED)
        removedIndex = originalText.length
      }
      '>' -> {
        part.removeLast()
        if (firstSplitPartIndex != null) {
          // The second part of the split range now starts.
          modifiedStyles += newStyle(removedIndex + insertedText.length)
        }
        else if (originalStyles.lastOrNull()?.startOffset?.let { it >= removedIndex } == true && originalStyles.lastOrNull()?.isEmpty() == true) {
          // The last style range in the removed part is not removed entirely, so we need to include a part of it into the modified text.
          modifiedStyles += newStyle(modifiedText.length)
        }
      }
      '|' -> {
        part.removeLast()
        part.addLast(Part.INSERTED)
        if (modifiedStyles.lastOrNull()?.startOffset?.let { it < removedIndex } == true && modifiedStyles.lastOrNull()?.isEmpty() == true) {
          // The previous style range crosses the removed part, so we need to split it.
          firstSplitPartIndex = modifiedStyles.lastIndex
          modifiedStyles.updateLast { it.copy(endOffset = removedIndex.toLong()) }
        }
      }
      else -> {
        when (part.last()) {
          Part.UNAFFECTED -> {
            originalText.append(c)
            modifiedText.append(c)
          }
          Part.REMOVED -> {
            originalText.append(c)
            ++removedLength
          }
          Part.INSERTED -> {
            modifiedText.append(c)
            insertedText.append(c)
          }
          Part.STYLE_INDEX -> {
            styleIndexText.append(c)
          }
        }
      }
    }
  }
  return TextWithReplacement(
    TextWithStyles(originalText.toString(), originalStyles),
    TextWithStyles(modifiedText.toString(), modifiedStyles),
    removedIndex,
    removedLength,
    TextWithStyles(insertedText.toString(), insertedStyles),
  )
}

private inline fun MutableList<StyleRange>.updateLast(modification: (StyleRange) -> StyleRange) {
  this[lastIndex] = modification(last())
}

private fun StyleRange.isEmpty(): Boolean = startOffset == endOffset

private enum class Part { UNAFFECTED, REMOVED, INSERTED, STYLE_INDEX }

internal data class TextWithReplacement(
  val originalText: TextWithStyles,
  val modifiedText: TextWithStyles,
  val removedIndex: Int,
  val removedLength: Int,
  val insertedText: TextWithStyles,
) {
  companion object {
    val STYLE1 = TextStyle(TerminalColor.BLACK, null)
    val STYLE2 = TextStyle(TerminalColor.WHITE, null)
    val STYLE3 = TextStyle(null, TerminalColor.BLACK)
    val STYLE4 = TextStyle(null, TerminalColor.WHITE)
  }
}

internal data class TextWithStyles(val text: String, val styles: List<StyleRange>) {
  init {
    assertThat(text).doesNotContain("[", "]", "<", ">", "(", ")", "|") // protection against parse errors and invalid arguments
  }
}

