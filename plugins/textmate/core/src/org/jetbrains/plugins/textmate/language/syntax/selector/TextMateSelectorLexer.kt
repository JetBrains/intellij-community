package org.jetbrains.plugins.textmate.language.syntax.selector

import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorToken.PriorityToken
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorToken.SelectorToken

object TextMateSelectorLexer {
  fun tokenize(selector: CharSequence): List<TextMateSelectorToken> {
    val result = mutableListOf<TextMateSelectorToken>()
    var currentSelector = StringBuilder()
    var i = 0
    while (i < selector.length) {
      val c = selector[i]
      when {
        c == '(' -> {
          currentSelector = addPendingToken(result, currentSelector)
          result.add(TextMateSelectorToken.LPAREN)
        }
        c == ')' -> {
          currentSelector = addPendingToken(result, currentSelector)
          result.add(TextMateSelectorToken.RPAREN)
        }
        c == ',' -> {
          currentSelector = addPendingToken(result, currentSelector)
          result.add(TextMateSelectorToken.COMMA)
        }
        c == '|' -> {
          currentSelector = addPendingToken(result, currentSelector)
          result.add(TextMateSelectorToken.PIPE)
        }
        c == '^' -> {
          currentSelector = addPendingToken(result, currentSelector)
          result.add(TextMateSelectorToken.HAT)
        }
        c == '-' && currentSelector.isEmpty() -> {
          currentSelector = addPendingToken(result, currentSelector)
          result.add(TextMateSelectorToken.MINUS)
        }
        c == ' ' -> {
          currentSelector = addPendingToken(result, currentSelector)
        }
        (c == 'R' || c == 'L' || c == 'B') && i + 1 < selector.length && selector[i + 1] == ':' -> {
          currentSelector = addPendingToken(result, currentSelector)
          i++
          if (c == 'R') {
            result.add(PriorityToken(TextMateWeigh.Priority.LOW))
          }
          else if (c == 'L') {
            result.add(PriorityToken(TextMateWeigh.Priority.HIGH))
          }
        }
        else -> {
          currentSelector.append(c)
        }
      }
      i++
    }
    addPendingToken(result, currentSelector)
    return result
  }

  private fun addPendingToken(result: MutableList<TextMateSelectorToken>, currentSelector: StringBuilder): StringBuilder {
    if (!currentSelector.isEmpty()) {
      result.add(SelectorToken(currentSelector.toString()))
      return StringBuilder()
    }
    return currentSelector
  }
}
