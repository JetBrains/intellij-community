package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.language.syntax.SyntaxNodeDescriptor;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateWeigh;
import org.jetbrains.plugins.textmate.regex.MatchData;
import org.jetbrains.plugins.textmate.regex.TextMateString;

import java.util.Objects;

public final class TextMateLexerState {
  private final int hashcode;
  public final @NotNull SyntaxNodeDescriptor syntaxRule;
  public final @NotNull MatchData matchData;
  public final @NotNull TextMateWeigh.Priority priorityMatch;
  // offset in line where state was emitted. used for local loop protection only
  public final int enterByteOffset;
  public final boolean matchedEOL;
  public final @Nullable TextMateString string;

  TextMateLexerState(@NotNull SyntaxNodeDescriptor syntaxRule,
                     @NotNull MatchData matchData,
                     @NotNull TextMateWeigh.Priority priority,
                     int enterByteOffset,
                     @Nullable TextMateString line) {
    this.syntaxRule = syntaxRule;
    this.matchData = matchData;
    this.priorityMatch = priority;
    this.enterByteOffset = enterByteOffset;
    this.matchedEOL = matchData.matched && line != null && matchData.byteOffset().end == line.bytes.length;
    string = matchData.matched ? line : null;
    hashcode = Objects.hash(syntaxRule, matchData, priorityMatch, stringId());
  }

  public static TextMateLexerState notMatched(@NotNull SyntaxNodeDescriptor syntaxRule) {
    return new TextMateLexerState(syntaxRule, MatchData.NOT_MATCHED, TextMateWeigh.Priority.NORMAL, 0,null);
  }

  @Override
  public @NotNull String toString() {
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

  private @Nullable Object stringId() {
    return string != null ? string.id : null;
  }
}
