package org.jetbrains.plugins.textmate.language.preferences;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.language.PreferencesReadUtil;
import org.jetbrains.plugins.textmate.language.TextMateScopeComparator;
import org.jetbrains.plugins.textmate.plist.Plist;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Table of textmate preferences.
 * Table represents mapping from scopeNames to set of preferences {@link Preferences}
 */
public class PreferencesRegistry {
  @NotNull private final Set<Preferences> myPreferences = new HashSet<>();

  /**
   * Append table with new preferences
   */
  public void fillFromPList(@NotNull String scopeName, @NotNull Plist plist) {
    final Set<TextMateBracePair> highlightingPairs = PreferencesReadUtil.readPairs(plist.getPlistValue(Constants.HIGHLIGHTING_PAIRS_KEY));
    final Set<TextMateBracePair> smartTypingPairs = PreferencesReadUtil.readPairs(plist.getPlistValue(Constants.SMART_TYPING_PAIRS_KEY));
    if (highlightingPairs != null || smartTypingPairs != null) {
      myPreferences.add(new Preferences(scopeName, highlightingPairs, smartTypingPairs));
    }
  }

  /**
   * Returns preferences by scope selector.
   *
   * @param scopeSelector selector of current context.
   * @return preferences from table for given scope sorted by descending weigh
   *         of rule selector relative to scope selector.
   */
  @NotNull
  public List<Preferences> getPreferences(@NotNull String scopeSelector) {
    return new TextMateScopeComparator<Preferences>(scopeSelector).sortAndFilter(myPreferences);
  }

  public void clear() {
    myPreferences.clear();
  }
}
