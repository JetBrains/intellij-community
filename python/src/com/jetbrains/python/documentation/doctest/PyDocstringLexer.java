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
          advanceBase();
        }
      }
    }
    else if (super.getTokenType() == PyTokenTypes.GTGT) {
      super.advance();
      if (super.getTokenType() == PyTokenTypes.GT) {
        super.advance();
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
    while (getBaseTokenType() != null && PyTokenTypes.WHITESPACE_OR_LINEBREAK.contains(getBaseTokenType())) {
      if (getBaseTokenType() == PyTokenTypes.TAB) {
        indent = ((indent / 8) + 1) * 8;
      }
      else if (getBaseTokenType() == PyTokenTypes.SPACE) {
        indent++;
      }
      else if (getBaseTokenType() == PyTokenTypes.LINE_BREAK) {
        indent = 0;
      }
      advanceBase();
    }
    if (getBaseTokenType() == null) {
      return 0;
    }
    return indent - 1;
  }
}
