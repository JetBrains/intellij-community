package org.jetbrains.plugins.textmate.language.preferences

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.plugins.textmate.language.TextMateScopeComparatorCore
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigher
import java.util.concurrent.ConcurrentHashMap

class SnippetsRegistryImpl(private val weigher: TextMateSelectorWeigher) : SnippetsRegistry {
  private val mySnippets: MutableMap<String?, PersistentList<TextMateSnippet>> = ConcurrentHashMap<String?, PersistentList<TextMateSnippet>>()

  fun register(snippet: TextMateSnippet) {
    mySnippets.compute(snippet.key) { k, v ->
      v?.add(snippet) ?: persistentListOf(snippet)
    }
  }

  override fun findSnippet(key: String, scope: TextMateScope?): Collection<TextMateSnippet> {
    if (scope == null) {
      return listOf<TextMateSnippet>()
    }
    val snippets = mySnippets[key]
    if (snippets == null) {
      return listOf<TextMateSnippet>()
    }
    return TextMateScopeComparatorCore(weigher, scope, TextMateSnippet::scopeSelector).sortAndFilter(snippets)
  }

  override fun getAvailableSnippets(scopeSelector: TextMateScope?): Collection<TextMateSnippet> {
    if (scopeSelector == null) {
      return listOf<TextMateSnippet>()
    }
    return TextMateScopeComparatorCore(weigher, scopeSelector, TextMateSnippet::scopeSelector)
      .sortAndFilter(mySnippets.values.flatMap { it })
  }

  fun clear() {
    mySnippets.clear()
  }
}
