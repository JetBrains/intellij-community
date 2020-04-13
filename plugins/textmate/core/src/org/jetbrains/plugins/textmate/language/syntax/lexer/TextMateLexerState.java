package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.language.syntax.SyntaxNodeDescriptor;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateWeigh;
import org.jetbrains.plugins.textmate.regex.MatchData;
import org.jetbrains.plugins.textmate.regex.StringWithId;

import java.util.Objects;

public class TextMateLexerState {
  private final int hashcode;
  @NotNull public final SyntaxNodeDescriptor syntaxRule;
  @NotNull public final MatchData matchData;
  @NotNull public final TextMateWeigh.Priority priorityMatch;
  @Nullable public final StringWithId string;

  TextMateLexerState(@NotNull SyntaxNodeDescriptor syntaxRule,
                     @NotNull MatchData matchData,
                     @NotNull TextMateWeigh.Priority priority,
                     @Nullable StringWithId line) {
    this.syntaxRule = syntaxRule;
    this.matchData = matchData;
    this.priorityMatch = priority;
    string = matchData.matched() ? line : null;
    hashcode = Objects.hash(syntaxRule, matchData, priorityMatch, stringId());
  }

  public static TextMateLexerState notMatched(@NotNull SyntaxNodeDescriptor syntaxRule) {
    return new TextMateLexerState(syntaxRule, MatchData.NOT_MATCHED, TextMateWeigh.Priority.NORMAL, null);
  }

  @NotNull
  @Override
  public String toString() {
    return "TextMateLexerState{" +
           "syntaxRule=" + syntaxRule +
           ", matchData=" + matchData +
           '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TextMateLexerState state = (TextMateLexerState)o;
    return syntaxRule.equals(state.syntaxRule) &&
           matchData.equals(state.matchData) &&
           priorityMatch == state.priorityMatch &&
           stringId() == state.stringId();
  }

  @Override
  public int hashCode() {
    return hashcode;
  }

  @Nullable
  private Object stringId() {
    return string != null ? string.id : null;
  }
}
