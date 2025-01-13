package org.jetbrains.plugins.textmate.language.preferences;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.language.TextMateScopeComparator;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SnippetsRegistryImpl implements SnippetsRegistry {
  private final @NotNull Map<String, Collection<TextMateSnippet>> mySnippets = new ConcurrentHashMap<>();

  public void register(@NotNull TextMateSnippet snippet) {
    mySnippets.computeIfAbsent(snippet.getKey(), (key) -> Collections.synchronizedList(new ArrayList<>())).add(snippet);
  }

  @Override
  public @NotNull Collection<TextMateSnippet> findSnippet(@NotNull String key, @Nullable TextMateScope scope) {
    if (scope == null) {
      return Collections.emptyList();
    }
    Collection<TextMateSnippet> snippets = mySnippets.get(key);
    if (snippets == null) {
      return Collections.emptyList();
    }
    return new TextMateScopeComparator<>(scope, TextMateSnippet::getScopeSelector).sortAndFilter(snippets);
  }

  @Override
  public @NotNull Collection<TextMateSnippet> getAvailableSnippets(@Nullable TextMateScope scopeSelector) {
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
