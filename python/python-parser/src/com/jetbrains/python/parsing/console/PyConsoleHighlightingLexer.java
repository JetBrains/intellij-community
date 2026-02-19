// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.parsing.console;

import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.lexer.PythonHighlightingLexer;
import com.jetbrains.python.lexer.PythonLexerKind;
import com.jetbrains.python.psi.LanguageLevel;

public class PyConsoleHighlightingLexer extends PythonHighlightingLexer {
  public PyConsoleHighlightingLexer(LanguageLevel languageLevel) {
    super(languageLevel, PythonLexerKind.CONSOLE);
  }

  /**
   * Treats special symbols used in IPython console
   */
  @Override
  public IElementType getTokenType() {
    IElementType type = super.getTokenType();
    if (type == PyTokenTypes.BAD_CHARACTER && PythonConsoleLexer.isSpecialSymbols(getTokenText())) {
      type = PythonConsoleLexer.getElementType(getTokenText());
    }

    return type;
  }
}
