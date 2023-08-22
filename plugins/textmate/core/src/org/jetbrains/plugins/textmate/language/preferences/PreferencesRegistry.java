package org.jetbrains.plugins.textmate.language.preferences;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope;

import java.util.List;

/**
 * Table of textmate preferences.
 * Table represents mapping from scopeNames to set of preferences {@link Preferences}
 */
public interface PreferencesRegistry {

  boolean isPossibleLeftHighlightingBrace(char c);

  boolean isPossibleRightHighlightingBrace(char c);

  boolean isPossibleLeftSmartTypingBrace(char c);

  boolean isPossibleRightSmartTypingBrace(char c);

  /**
   * Returns preferences by scope selector.
   *
   * @param scope selector of current context.
   * @return preferences from table for given scope sorted by descending weigh
   * of rule selector relative to scope selector.
   */
  @NotNull
  List<Preferences> getPreferences(@NotNull TextMateScope scope);
}