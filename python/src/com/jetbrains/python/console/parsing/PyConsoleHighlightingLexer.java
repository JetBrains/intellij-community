package com.jetbrains.python.console.parsing;

import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.lexer.PythonHighlightingLexer;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * @author traff
 */
public class PyConsoleHighlightingLexer extends PythonHighlightingLexer {
  public PyConsoleHighlightingLexer(LanguageLevel languageLevel) {
    super(languageLevel);
  }

  @Override
  /**
   * Treats special symbols used in IPython console
   */
  public IElementType getTokenType() {
    IElementType type = super.getTokenType();
    if (type == PyTokenTypes.BAD_CHARACTER && PythonConsoleLexer.isSpecialSymbols(getTokenText())) {
      type = PythonConsoleLexer.getElementType(getTokenText());
    }

    return type;
  }
}
