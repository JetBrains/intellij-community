package org.jetbrains.plugins.textmate.language.syntax.lexer

data class TextmateToken(
  val scope: TextMateScope,
  val startOffset: Int,
  val endOffset: Int,
  val restartable: Boolean,
)
