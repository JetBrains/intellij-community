package org.jetbrains.plugins.textmate.language.syntax.selector;

public interface TextMateSelectorToken {
  TextMateSelectorToken COMMA = new TextMateSelectorLexer.SignToken(',');
  TextMateSelectorToken LPAREN = new TextMateSelectorLexer.SignToken('(');
  TextMateSelectorToken RPAREN = new TextMateSelectorLexer.SignToken(')');
  TextMateSelectorToken PIPE = new TextMateSelectorLexer.SignToken('|');
  TextMateSelectorToken MINUS = new TextMateSelectorLexer.SignToken('-');
  TextMateSelectorToken HAT = new TextMateSelectorLexer.SignToken('^');
}
