package org.jetbrains.plugins.textmate.language;

import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.editor.TextMateSnippet;

import java.util.Collection;
import java.util.Collections;

public class SnippetsRegistry {
  @NotNull private final MultiMap<String, TextMateSnippet> mySnippets = MultiMap.create();

  public void register(@NotNull TextMateSnippet snippet) {
    mySnippets.putValue(snippet.getKey(), snippet);
  }

  @NotNull
  public Collection<TextMateSnippet> findSnippet(@NotNull String key, @Nullable String scopeSelector) {
    if (scopeSelector == null) {
      return Collections.emptyList();
    }
    return new TextMateScopeComparator<TextMateSnippet>(scopeSelector).sortAndFilter(mySnippets.get(key));
  }

  @NotNull
  public Collection<TextMateSnippet> getAvailableSnippets(@Nullable String scopeSelector) {
    if (scopeSelector == null) {
      return Collections.emptyList();
    }
    return new TextMateScopeComparator<TextMateSnippet>(scopeSelector).sortAndFilter(mySnippets.values());
  }

  public void clear() {
    mySnippets.clear();
  }
}
