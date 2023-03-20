package org.jetbrains.plugins.textmate.language.preferences;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.language.PreferencesReadUtil;
import org.jetbrains.plugins.textmate.language.TextMateScopeComparator;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope;
import org.jetbrains.plugins.textmate.plist.Plist;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PreferencesRegistryImpl implements PreferencesRegistry {
  @NotNull private final Set<Preferences> myPreferences = new HashSet<>();
  @NotNull private final IntSet myLeftHighlightingBraces = new IntOpenHashSet();
  @NotNull private final IntSet myRightHighlightingBraces = new IntOpenHashSet();
  @NotNull private final IntSet myLeftSmartTypingBraces = new IntOpenHashSet();
  @NotNull private final IntSet myRightSmartTypingBraces = new IntOpenHashSet();

  public PreferencesRegistryImpl() {
    fillHighlightingBraces(Constants.DEFAULT_HIGHLIGHTING_BRACE_PAIRS);
    fillSmartTypingBraces(Constants.DEFAULT_SMART_TYPING_BRACE_PAIRS);
  }

  public void addPreferences(Preferences preferences) {
    fillHighlightingBraces(preferences.getHighlightingPairs());
    fillSmartTypingBraces(preferences.getSmartTypingPairs());
    myPreferences.add(preferences);
  }

  /**
   * Append table with new preferences
   *
   * @deprecated use {@link this#addPreferences(Preferences)} instead
   */
  @Deprecated(forRemoval = true)
  public void fillFromPList(@NotNull CharSequence scopeName, @NotNull Plist plist) {
    final Set<TextMateBracePair> highlightingPairs = PreferencesReadUtil.readPairs(plist.getPlistValue(Constants.HIGHLIGHTING_PAIRS_KEY));
    final Set<TextMateBracePair> smartTypingPairs = PreferencesReadUtil.readPairs(plist.getPlistValue(Constants.SMART_TYPING_PAIRS_KEY));
    final IndentationRules indentationRules = PreferencesReadUtil.loadIndentationRules(plist);
    fillHighlightingBraces(highlightingPairs);
    fillSmartTypingBraces(smartTypingPairs);
    if (highlightingPairs != null || smartTypingPairs != null || !indentationRules.isEmpty()) {
      myPreferences.add(new Preferences(scopeName, highlightingPairs, smartTypingPairs, indentationRules));
    }
  }

  private void fillHighlightingBraces(Collection<TextMateBracePair> highlightingPairs) {
    if (highlightingPairs != null) {
      for (TextMateBracePair pair : highlightingPairs) {
        myLeftHighlightingBraces.add(pair.leftChar);
        myRightHighlightingBraces.add(pair.rightChar);
      }
    }
  }

  private void fillSmartTypingBraces(Collection<TextMateBracePair> smartTypingPairs) {
    if (smartTypingPairs != null) {
      for (TextMateBracePair pair : smartTypingPairs) {
        myLeftSmartTypingBraces.add(pair.leftChar);
        myRightSmartTypingBraces.add(pair.rightChar);
      }
    }
  }

  @Override
  public boolean isPossibleLeftHighlightingBrace(char c) {
    return myLeftHighlightingBraces.contains(c) || (c != ' ' && myLeftSmartTypingBraces.contains(c));
  }

  @Override
  public boolean isPossibleRightHighlightingBrace(char c) {
    return myRightHighlightingBraces.contains(c) || (c != ' ' && myRightSmartTypingBraces.contains(c));
  }

  @Override
  public boolean isPossibleLeftSmartTypingBrace(char c) {
    return myLeftSmartTypingBraces.contains(c);
  }

  @Override
  public boolean isPossibleRightSmartTypingBrace(char c) {
    return myRightSmartTypingBraces.contains(c);
  }

  /**
   * Returns preferences by scope selector.
   *
   * @param scope selector of current context.
   * @return preferences from table for given scope sorted by descending weigh
   * of rule selector relative to scope selector.
   */
  @Override @NotNull
  public List<Preferences> getPreferences(@NotNull TextMateScope scope) {
    return new TextMateScopeComparator<>(scope, Preferences::getScopeSelector).sortAndFilter(myPreferences);
  }

  public void clear() {
    myPreferences.clear();

    myLeftHighlightingBraces.clear();
    myRightHighlightingBraces.clear();
    fillHighlightingBraces(Constants.DEFAULT_HIGHLIGHTING_BRACE_PAIRS);

    myLeftSmartTypingBraces.clear();
    myRightSmartTypingBraces.clear();
    fillSmartTypingBraces(Constants.DEFAULT_SMART_TYPING_BRACE_PAIRS);
  }
}
