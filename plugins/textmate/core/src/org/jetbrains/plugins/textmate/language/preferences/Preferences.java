package org.jetbrains.plugins.textmate.language.preferences;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.language.TextMateScopeSelectorOwner;

import java.util.Set;

public class Preferences implements TextMateScopeSelectorOwner {
  @NotNull private final CharSequence myScopeRule;
  @Nullable private final Set<TextMateBracePair> myHighlightingPairs;
  @Nullable private final Set<TextMateBracePair> mySmartTypingPairs;
  @NotNull private final IndentationRules myIndentationRules;

  public Preferences(@NotNull CharSequence scopeRule,
                     @Nullable Set<TextMateBracePair> highlightingPairs,
                     @Nullable Set<TextMateBracePair> smartTypingPairs,
                     @NotNull IndentationRules indentationRules) {
    myScopeRule = scopeRule;
    myHighlightingPairs = highlightingPairs;
    mySmartTypingPairs = smartTypingPairs;
    myIndentationRules = indentationRules;
  }

  @Nullable
  public Set<TextMateBracePair> getHighlightingPairs() {
    return myHighlightingPairs;
  }

  @Nullable
  public Set<TextMateBracePair> getSmartTypingPairs() {
    return mySmartTypingPairs;
  }

  @NotNull
  @Override
  public CharSequence getScopeSelector() {
    return myScopeRule;
  }

  @NotNull
  public IndentationRules getIndentationRules() { return myIndentationRules; }
}