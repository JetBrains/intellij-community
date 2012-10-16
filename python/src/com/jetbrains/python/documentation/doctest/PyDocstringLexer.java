package com.jetbrains.python.documentation.doctest;

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

}
