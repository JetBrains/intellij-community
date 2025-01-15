package org.jetbrains.plugins.textmate.language.syntax.selector

import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope

/**
 * Responsibility of instances is calculating weigh of color-highlighting rule relative to some selectors.
 * Should be used in order to define which color-highlighting rule is most appropriate for given selector.
 *
 * See [Ranking Matches documentation](http://manual.macromates.com/en/scope_selectors#ranking_matches)
 */
interface TextMateSelectorWeigher {
  /**
   * Calculates weight of ${scopeSelector} relative to ${scope}.
   *
   * Corresponding to documentation, ${scopeSelector} can contain several selectors
   * delimited by comma.
   *
   * @param scopeSelector     It could be highlighting rule from TextMate color scheme or scope name of preferences.
   * @param scope scope selector of a target element.
   * @return relative score (non-negative int)
   */
  fun weigh(scopeSelector: CharSequence, scope: TextMateScope): TextMateWeigh
}
