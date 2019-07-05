package org.jetbrains.plugins.textmate.language.syntax.lexer;

import com.intellij.lexer.LexerState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.language.syntax.SyntaxNodeDescriptor;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateWeigh;
import org.jetbrains.plugins.textmate.regex.MatchData;

import java.util.Arrays;
import java.util.Objects;

class TextMateLexerState implements LexerState {
  @NotNull public final SyntaxNodeDescriptor syntaxRule;
  @NotNull public final MatchData matchData;
  @NotNull public final TextMateWeigh.Priority priorityMatch;
  @Nullable public final byte[] stringBytes;

  TextMateLexerState(@NotNull SyntaxNodeDescriptor syntaxRule,
                     @NotNull MatchData matchData,
                     @NotNull TextMateWeigh.Priority priority,
                     @Nullable byte[] lineBytes) {
    this.syntaxRule = syntaxRule;
    this.matchData = matchData;
    this.priorityMatch = priority;
    stringBytes = matchData.matched() ? lineBytes : null;
  }

  public static TextMateLexerState notMatched(@NotNull SyntaxNodeDescriptor syntaxRule) {
    return new TextMateLexerState(syntaxRule, MatchData.NOT_MATCHED, TextMateWeigh.Priority.NORMAL, null);
  }

  @Override
  public short intern() {
    return 0;
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
           Arrays.equals(stringBytes, state.stringBytes);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(syntaxRule, matchData, priorityMatch);
    result = 31 * result + Arrays.hashCode(stringBytes);
    return result;
  }
}
