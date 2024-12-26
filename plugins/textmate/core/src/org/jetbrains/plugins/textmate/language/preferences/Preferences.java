package org.jetbrains.plugins.textmate.language.preferences;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.language.TextMateScopeSelectorOwner;

import java.util.Set;

public final class Preferences implements TextMateScopeSelectorOwner {
  private final @NotNull CharSequence myScopeRule;
  private final @Nullable Set<TextMateBracePair> myHighlightingPairs;
  private final @Nullable Set<TextMateAutoClosingPair> mySmartTypingPairs;
  private final @Nullable Set<TextMateBracePair> mySurroundingPairs;
  private final @Nullable String myAutoCloseBefore;
  private final @NotNull IndentationRules myIndentationRules;
  private final @Nullable Set<OnEnterRule> myOnEnterRules;

  public Preferences(@NotNull CharSequence scopeRule,
                     @Nullable Set<TextMateBracePair> highlightingPairs,
                     @Nullable Set<TextMateAutoClosingPair> smartTypingPairs,
                     @Nullable Set<TextMateBracePair> surroundingPairs,
                     @Nullable String autoCloseBefore,
                     @NotNull IndentationRules indentationRules,
                     @Nullable Set<OnEnterRule> onEnterRules) {
    myScopeRule = scopeRule;
    myHighlightingPairs = highlightingPairs;
    mySmartTypingPairs = smartTypingPairs;
    mySurroundingPairs = surroundingPairs;
    myAutoCloseBefore = autoCloseBefore;
    myIndentationRules = indentationRules;
    myOnEnterRules = onEnterRules;
  }

  public @Nullable Set<TextMateBracePair> getHighlightingPairs() {
    return myHighlightingPairs;
  }

  public @Nullable Set<TextMateAutoClosingPair> getSmartTypingPairs() {
    return mySmartTypingPairs;
  }

  public @Nullable Set<TextMateBracePair> getSurroundingPairs() {
    return mySurroundingPairs;
  }

  public @Nullable String getAutoCloseBefore() {
    return myAutoCloseBefore;
  }

  @Override
  public @NotNull CharSequence getScopeSelector() {
    return myScopeRule;
  }

  public @NotNull IndentationRules getIndentationRules() { return myIndentationRules; }

  public @Nullable Set<OnEnterRule> getOnEnterRules() { return myOnEnterRules; }
}