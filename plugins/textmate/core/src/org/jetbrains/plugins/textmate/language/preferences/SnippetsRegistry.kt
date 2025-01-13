package org.jetbrains.plugins.textmate.language.preferences;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope;

import java.util.Collection;

public interface SnippetsRegistry {

  @NotNull
  Collection<TextMateSnippet> findSnippet(@NotNull String key, @Nullable TextMateScope scope);

  @NotNull
  Collection<TextMateSnippet> getAvailableSnippets(@Nullable TextMateScope scopeSelector);
}
