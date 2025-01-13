package org.jetbrains.plugins.textmate.language.preferences

import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope

interface SnippetsRegistry {
  fun findSnippet(key: String, scope: TextMateScope?): Collection<TextMateSnippet>

  fun getAvailableSnippets(scopeSelector: TextMateScope?): Collection<TextMateSnippet>
}
