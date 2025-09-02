package org.jetbrains.plugins.textmate.language.preferences

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.jetbrains.plugins.textmate.language.TextMateScopeComparatorCore
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigher
import org.jetbrains.plugins.textmate.update
import kotlin.concurrent.atomics.AtomicReference

class SnippetsRegistryBuilder(private val weigher: TextMateSelectorWeigher) {
  private val snippets = AtomicReference(persistentMapOf<String, PersistentList<TextMateSnippet>>())

  fun register(snippet: TextMateSnippet) {
    snippets.update {
      it.put(snippet.key, it[snippet.key]?.add(snippet) ?: persistentListOf(snippet))
    }
  }

  fun build(): SnippetsRegistry {
    return SnippetsRegistryImpl(weigher, snippets.load())
  }
}

class SnippetsRegistryImpl(
  private val weigher: TextMateSelectorWeigher,
  private val snippets: Map<String, List<TextMateSnippet>>,
) : SnippetsRegistry {
  override fun findSnippet(key: String, scope: TextMateScope?): Collection<TextMateSnippet> {
    if (scope == null) {
      return emptyList()
    }
    val snippets = snippets[key] ?: return emptyList()
    return TextMateScopeComparatorCore(weigher, scope, TextMateSnippet::scopeSelector).sortAndFilter(snippets)
  }

  override fun getAvailableSnippets(scopeSelector: TextMateScope?): Collection<TextMateSnippet> {
    if (scopeSelector == null) {
      return emptyList()
    }
    return TextMateScopeComparatorCore(weigher, scopeSelector, TextMateSnippet::scopeSelector)
      .sortAndFilter(snippets.values.flatMap { it })
  }
}
