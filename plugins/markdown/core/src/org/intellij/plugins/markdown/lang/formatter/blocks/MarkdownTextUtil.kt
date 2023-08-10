// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.formatter.blocks

import com.intellij.openapi.util.TextRange
import org.intellij.markdown.html.isPunctuation

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

  fun Char.isPunctuation(): Boolean {
    return isPunctuation(this)
  }
}
