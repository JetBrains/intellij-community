// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.util

import com.intellij.openapi.util.TextRange

/**
 * Text utilities used mainly by Markdown formatter
 * to perform accurate formatting and reflow of text.
 */
internal object MarkdownTextUtil {
  fun getTrimmedRange(text: String, shift: Int = 0): TextRange {
    val dropStart = text.takeWhile { it.isWhitespace() }.count()
    val dropLast = text.reversed().takeWhile { it.isWhitespace() }.count()
    if (dropStart + dropLast >= text.length) return TextRange.EMPTY_RANGE

    return TextRange.from(shift + dropStart, text.length - dropLast - dropStart)
  }

  fun getSplitBySpacesRanges(text: String, shift: Int = 0): Sequence<TextRange> = sequence {
    var start = -1
    var length = -1
    for ((index, char) in text.withIndex()) {
      if (char.isWhitespace()) {
        if (length > 0) {
          yield(TextRange.from(shift + start, length))
        }
        start = -1
        length = -1
      }
      else {
        if (start == -1) {
          start = index
          length = 0
        }
        length++
      }
    }

    if (length > 0) {
      yield(TextRange.from(shift + start, length))
    }
  }

}