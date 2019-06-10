package org.jetbrains.plugins.textmate.language.syntax.lexer;

import com.intellij.lexer.LexerState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.language.syntax.SyntaxNodeDescriptor;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateWeigh;
import org.jetbrains.plugins.textmate.regex.MatchData;

class TextMateLexerState implements LexerState {
  @NotNull public final SyntaxNodeDescriptor syntaxRule;
  @NotNull public final MatchData matchData;
  @NotNull public final TextMateWeigh.Priority priorityMatch;

  TextMateLexerState(@NotNull SyntaxNodeDescriptor syntaxRule,
                     @NotNull MatchData matchData,
                     @NotNull TextMateWeigh.Priority priority) {
    this.syntaxRule = syntaxRule;
    this.matchData = matchData;
    this.priorityMatch = priority;
  }

  public static TextMateLexerState notMatched(@NotNull SyntaxNodeDescriptor syntaxRule) {
    return new TextMateLexerState(syntaxRule, MatchData.NOT_MATCHED, TextMateWeigh.Priority.NORMAL);
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

    if (!matchData.equals(state.matchData)) return false;
    if (!syntaxRule.getScopeName().equals(state.syntaxRule.getScopeName())) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = syntaxRule.getScopeName().hashCode();
    result = 31 * result + matchData.hashCode();
    return result;
  }
}
