package org.jetbrains.plugins.textmate.language.syntax.lexer

import org.jetbrains.plugins.textmate.regex.TextMateCharOffset

data class TextmateToken(
  val scope: TextMateScope,
  val startCharOffset: TextMateCharOffset,
  val endCharOffset: TextMateCharOffset,
  val restartable: Boolean,
) {
  val startOffset: Int
    get() = startCharOffset.offset
  val endOffset: Int
    get() = endCharOffset.offset
}
