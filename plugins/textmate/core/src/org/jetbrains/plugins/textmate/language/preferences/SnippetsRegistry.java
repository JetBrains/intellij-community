package org.jetbrains.plugins.textmate.language.preferences;

import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.language.TextMateScopeComparator;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope;

import java.util.Collection;
import java.util.Collections;

public class SnippetsRegistry {
  @NotNull private final MultiMap<String, TextMateSnippet> mySnippets = MultiMap.create();

  public void register(@NotNull TextMateSnippet snippet) {
    mySnippets.putValue(snippet.getKey(), snippet);
  }

  @NotNull
  public Collection<TextMateSnippet> findSnippet(@NotNull String key, @Nullable TextMateScope scope) {
    if (scope == null) {
      return Collections.emptyList();
    }
    return new TextMateScopeComparator<>(scope, TextMateSnippet::getScopeSelector).sortAndFilter(mySnippets.get(key));
  }

  @NotNull
  public Collection<TextMateSnippet> getAvailableSnippets(@Nullable TextMateScope scopeSelector) {
    if (scopeSelector == null) {
      return Collections.emptyList();
    }
    return new TextMateScopeComparator<>(scopeSelector, TextMateSnippet::getScopeSelector).sortAndFilter(mySnippets.values());
  }

  public void clear() {
    mySnippets.clear();
  }
}
