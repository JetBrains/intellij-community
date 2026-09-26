package org.jetbrains.plugins.textmate.language.syntax.selector

sealed class TextMateSelectorToken {
  companion object {
    val COMMA: TextMateSelectorToken = SignToken(',')
    val LPAREN: TextMateSelectorToken = SignToken('(')
    val RPAREN: TextMateSelectorToken = SignToken(')')
    val PIPE: TextMateSelectorToken = SignToken('|')
    val MINUS: TextMateSelectorToken = SignToken('-')
    val HAT: TextMateSelectorToken = SignToken('^')
  }

  data class SignToken(val sign: Char) : TextMateSelectorToken()
  data class PriorityToken(val priority: TextMateWeigh.Priority) : TextMateSelectorToken()
  data class SelectorToken(val text: String) : TextMateSelectorToken()
}
