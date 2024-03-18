package org.jetbrains.plugins.textmate.language.preferences;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.language.TextMateScopeSelectorOwner;

import java.util.Set;

public final class Preferences implements TextMateScopeSelectorOwner {
  @NotNull private final CharSequence myScopeRule;
  @Nullable private final Set<TextMateBracePair> myHighlightingPairs;
  @Nullable private final Set<TextMateAutoClosingPair> mySmartTypingPairs;
  @Nullable private final Set<TextMateBracePair> mySurroundingPairs;
  @Nullable private final String myAutoCloseBefore;
  @NotNull private final IndentationRules myIndentationRules;
  @Nullable private final Set<OnEnterRule> myOnEnterRules;

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

  @Nullable
  public Set<TextMateBracePair> getHighlightingPairs() {
    return myHighlightingPairs;
  }

  @Nullable
  public Set<TextMateAutoClosingPair> getSmartTypingPairs() {
    return mySmartTypingPairs;
  }

  @Nullable
  public Set<TextMateBracePair> getSurroundingPairs() {
    return mySurroundingPairs;
  }

  @Nullable
  public String getAutoCloseBefore() {
    return myAutoCloseBefore;
  }

  @NotNull
  @Override
  public CharSequence getScopeSelector() {
    return myScopeRule;
  }

  @NotNull
  public IndentationRules getIndentationRules() { return myIndentationRules; }

  @Nullable
  public Set<OnEnterRule> getOnEnterRules() { return myOnEnterRules; }
}