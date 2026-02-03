package org.jetbrains.plugins.textmate.language.syntax.selector

import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope

class TextMateSelectorWeigherImpl : TextMateSelectorWeigher {
  override fun weigh(scopeSelector: CharSequence, scope: TextMateScope): TextMateWeigh {
    return TextMateSelectorParser(scopeSelector).parse()?.weigh(scope) ?: TextMateWeigh.ZERO
  }
}