package org.jetbrains.plugins.textmate.language.preferences;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.language.TextMateScopeComparator;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope;

import java.util.*;

public class SnippetsRegistryImpl implements SnippetsRegistry {
  @NotNull private final Map<String, Collection<TextMateSnippet>> mySnippets = new HashMap<>();

  public void register(@NotNull TextMateSnippet snippet) {
    mySnippets.computeIfAbsent(snippet.getKey(), (key) ->  new ArrayList<>()).add(snippet);
  }

  @Override @NotNull
  public Collection<TextMateSnippet> findSnippet(@NotNull String key, @Nullable TextMateScope scope) {
    if (scope == null) {
      return Collections.emptyList();
    }
    Collection<TextMateSnippet> snippets = mySnippets.get(key);
    if (snippets == null) {
      return Collections.emptyList();
    }
    return new TextMateScopeComparator<>(scope, TextMateSnippet::getScopeSelector).sortAndFilter(snippets);
  }

  @Override @NotNull
  public Collection<TextMateSnippet> getAvailableSnippets(@Nullable TextMateScope scopeSelector) {
    if (scopeSelector == null) {
      return Collections.emptyList();
    }
    return new TextMateScopeComparator<>(scopeSelector, TextMateSnippet::getScopeSelector)
      .sortAndFilter(mySnippets.values().stream().flatMap((values) -> values.stream()));
  }

  public void clear() {
    mySnippets.clear();
  }
}
