package org.jetbrains.plugins.textmate.language.preferences

import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope

/**
 * Table of textmate preferences.
 * Table represents mapping from scopeNames to set of preferences [Preferences]
 */
interface PreferencesRegistry {
  fun isPossibleLeftHighlightingBrace(firstLeftBraceChar: Char): Boolean

  fun isPossibleRightHighlightingBrace(lastRightBraceChar: Char): Boolean

  fun isPossibleLeftSmartTypingBrace(lastLeftBraceChar: Char): Boolean

  fun isPossibleRightSmartTypingBrace(lastRightBraceChar: Char): Boolean

  /**
   * Returns preferences by scope selector.
   *
   * @param scope selector of current context.
   * @return preferences from table for given scope sorted by descending weigh
   * of rule selector relative to scope selector.
   */
  fun getPreferences(scope: TextMateScope): List<Preferences>
}