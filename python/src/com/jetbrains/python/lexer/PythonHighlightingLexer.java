package com.jetbrains.python.lexer;

import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * @author yole
 */
public class PythonHighlightingLexer extends PythonLexer {
  private final LanguageLevel myLanguageLevel;

  public PythonHighlightingLexer(LanguageLevel languageLevel) {
    myLanguageLevel = languageLevel;
  }

  @Override
  public IElementType getTokenType() {
    final String tokenText = getTokenText();
    if (myLanguageLevel.hasWithStatement()) {
      if (tokenText.equals("with")) return PyTokenTypes.WITH_KEYWORD;
      if (tokenText.equals("as")) return PyTokenTypes.AS_KEYWORD;
    }
    if (myLanguageLevel.hasPrintStatement()) {
      if (tokenText.equals("print")) return PyTokenTypes.PRINT_KEYWORD;
    }
    if (myLanguageLevel.isPy3K()) {
      if (tokenText.equals("None")) return PyTokenTypes.NONE_KEYWORD;
      if (tokenText.equals("True")) return PyTokenTypes.TRUE_KEYWORD;
      if (tokenText.equals("False")) return PyTokenTypes.FALSE_KEYWORD;
    }
    else {
      if (tokenText.equals("exec")) return PyTokenTypes.EXEC_KEYWORD;
    }
    return super.getTokenType();
  }
}
