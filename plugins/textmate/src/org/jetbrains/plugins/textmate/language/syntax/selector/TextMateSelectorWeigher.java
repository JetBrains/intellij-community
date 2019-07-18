package org.jetbrains.plugins.textmate.language.syntax.selector;

import org.jetbrains.annotations.NotNull;

/**
 * User: zolotov
 * <p/>
 * Responsibility of instances is calculating weigh of color-highlighting rule relative to some selectors.
 * Should be used in order to define which color-highlighting rule is most appropriate for given selector.
 * <p/>
 * See Ranking Matches documentation: http://manual.macromates.com/en/scope_selectors#ranking_matches
 */
public interface TextMateSelectorWeigher {

  /**
   * Calculates weigh of ${scopeSelector} relative to ${scope}.
   * <p/>
   * Corresponding to documentation, ${scopeSelector} can contains several selectors
   * delimited by comma.
   *
   *
   * @param scopeSelector     It could be highlighting rule from TextMate color scheme or scope name of preferences.
   * @param scope scope selector of target element.
   * @return relative score (nonnegative int)
   */
  TextMateWeigh weigh(@NotNull String scopeSelector, @NotNull String scope);
}
