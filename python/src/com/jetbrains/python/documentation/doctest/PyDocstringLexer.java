package com.jetbrains.python.documentation.doctest;

import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.lexer.PythonIndentingLexer;

/**
 * User : ktisha
 */
public class PyDocstringLexer extends PythonIndentingLexer {
  @Override
  public void advance() {
    if (super.getTokenType() == PyTokenTypes.DOT) {
      advanceBase();
      if (super.getTokenType() == PyTokenTypes.DOT) {
        advanceBase();
        if (super.getTokenType() == PyTokenTypes.DOT) {
          super.advance();
        }
      }
    }
    else if (super.getTokenType() == PyTokenTypes.GTGT) {
      advanceBase();
      if (super.getTokenType() == PyTokenTypes.GT) {
        advanceBase();
      }
    }
    else {
      super.advance();
    }
  }

  static final TokenSet ourIgnoreSet = TokenSet.create(PyTokenTypes.DOT, PyTokenTypes.GTGT, PyTokenTypes.GT);


  @Override
  protected int getNextLineIndent() {
    int indent = super.getNextLineIndent();
    if (!ourIgnoreSet.contains(getBaseTokenType()))
      return indent;

    indent = 0;
    while (getBaseTokenType() != null && ourIgnoreSet.contains(getBaseTokenType()))
      advanceBase();
    while (getBaseTokenType() != null && PyTokenTypes.WHITESPACE.contains(getBaseTokenType())) {
      if (getBaseTokenType() == PyTokenTypes.TAB) {
        indent = ((indent / 8) + 1) * 8;
      }
      else if (getBaseTokenType() == PyTokenTypes.SPACE) {
        indent++;
      }
      advanceBase();
    }
    if (getBaseTokenType() == null) {
      return 0;
    }
    return indent > 0? indent - 1 : indent;
  }
}
