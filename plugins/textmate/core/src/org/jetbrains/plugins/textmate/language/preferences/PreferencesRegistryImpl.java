package org.jetbrains.plugins.textmate.language.preferences;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.language.PreferencesReadUtil;
import org.jetbrains.plugins.textmate.language.TextMateScopeComparator;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope;
import org.jetbrains.plugins.textmate.plist.Plist;

import java.util.*;
import java.util.stream.Collectors;

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

  public synchronized void addPreferences(Preferences preferences) {
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
  public synchronized void fillFromPList(@NotNull CharSequence scopeName, @NotNull Plist plist) {
    final Set<TextMateBracePair> highlightingPairs = PreferencesReadUtil.readPairs(plist.getPlistValue(Constants.HIGHLIGHTING_PAIRS_KEY));
    Set<TextMateBracePair> rawSmartTypingPairs = PreferencesReadUtil.readPairs(plist.getPlistValue(Constants.SMART_TYPING_PAIRS_KEY));
    final Set<TextMateAutoClosingPair> smartTypingPairs = rawSmartTypingPairs != null ? rawSmartTypingPairs.stream().map(p -> {
      return new TextMateAutoClosingPair(p.getLeft(), p.getRight(), null);
    }).collect(Collectors.toSet()) : null;
    final IndentationRules indentationRules = PreferencesReadUtil.loadIndentationRules(plist);
    final Set<OnEnterRule> onEnterRules = Collections.emptySet(); // seems fine, since fillFromPList is deprecated anyway
    fillHighlightingBraces(highlightingPairs);
    fillSmartTypingBraces(smartTypingPairs);
    if (highlightingPairs != null || smartTypingPairs != null || !indentationRules.isEmpty()) {
      myPreferences.add(new Preferences(scopeName, highlightingPairs, smartTypingPairs, Collections.emptySet(), null, indentationRules, onEnterRules));
    }
  }

  private synchronized void fillHighlightingBraces(Collection<TextMateBracePair> highlightingPairs) {
    if (highlightingPairs != null) {
      for (TextMateBracePair pair : highlightingPairs) {
        if (!pair.getLeft().isEmpty()) {
          myLeftHighlightingBraces.add(pair.getLeft().charAt(0));
        }
        if (!pair.getRight().isEmpty()) {
          myRightHighlightingBraces.add(pair.getRight().charAt(pair.getRight().length() - 1));
        }
      }
    }
  }

  private void fillSmartTypingBraces(Collection<TextMateAutoClosingPair> smartTypingPairs) {
    if (smartTypingPairs != null) {
      for (TextMateAutoClosingPair pair : smartTypingPairs) {
        if (!pair.getLeft().isEmpty()) {
          myLeftSmartTypingBraces.add(pair.getLeft().charAt(pair.getLeft().length() - 1));
        }
        if (!pair.getRight().isEmpty()) {
          myRightSmartTypingBraces.add(pair.getRight().charAt(pair.getRight().length() - 1));
        }
      }
    }
  }

  @Override
  public synchronized boolean isPossibleLeftHighlightingBrace(char firstLeftBraceChar) {
    return myLeftHighlightingBraces.contains(firstLeftBraceChar) || (firstLeftBraceChar != ' ' && myLeftSmartTypingBraces.contains(
      firstLeftBraceChar));
  }

  @Override
  public synchronized boolean isPossibleRightHighlightingBrace(char lastRightBraceChar) {
    return myRightHighlightingBraces.contains(lastRightBraceChar) || (lastRightBraceChar != ' ' && myRightSmartTypingBraces.contains(
      lastRightBraceChar));
  }

  @Override
  public synchronized boolean isPossibleLeftSmartTypingBrace(char lastLeftBraceChar) {
    return myLeftSmartTypingBraces.contains(lastLeftBraceChar);
  }

  @Override
  public synchronized boolean isPossibleRightSmartTypingBrace(char lastRightBraceChar) {
    return myRightSmartTypingBraces.contains(lastRightBraceChar);
  }

  /**
   * Returns preferences by scope selector.
   *
   * @param scope selector of current context.
   * @return preferences from table for given scope sorted by descending weigh
   * of rule selector relative to scope selector.
   */
  @Override @NotNull
  public synchronized List<Preferences> getPreferences(@NotNull TextMateScope scope) {
    return new TextMateScopeComparator<>(scope, Preferences::getScopeSelector).sortAndFilter(myPreferences);
  }

  public synchronized void clear() {
    myPreferences.clear();

    myLeftHighlightingBraces.clear();
    myRightHighlightingBraces.clear();
    fillHighlightingBraces(Constants.DEFAULT_HIGHLIGHTING_BRACE_PAIRS);

    myLeftSmartTypingBraces.clear();
    myRightSmartTypingBraces.clear();
    fillSmartTypingBraces(Constants.DEFAULT_SMART_TYPING_BRACE_PAIRS);
  }
}
