// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.parsing.console;

import com.intellij.lexer.Lexer;
import com.intellij.lexer.MergeFunction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.lexer.PythonIndentingLexer;
import com.jetbrains.python.lexer.PythonLexerKind;
import com.jetbrains.python.psi.PyElementType;

import java.util.Map;

import static com.jetbrains.python.PyTokenTypes.*;
import static com.jetbrains.python.parsing.console.PyConsoleTokenTypes.MAGIC_COMMAND_LINE;
import static com.jetbrains.python.parsing.console.PyConsoleTokenTypes.SHELL_COMMAND;

public class PythonConsoleLexer extends PythonIndentingLexer {
  public PythonConsoleLexer() {
    super(PythonLexerKind.CONSOLE);
  }

  private final static Map<String, PyElementType> SPECIAL_IPYTHON_SYMBOLS = Map.of(
    "?", PyConsoleTokenTypes.QUESTION_MARK,
    "!", PyConsoleTokenTypes.PLING,
    "$", PyConsoleTokenTypes.DOLLAR
  );

  /**
   * Treats special symbols used in IPython console
   */
  @Override
  public IElementType getTokenType() {
    IElementType type = super.getTokenType();
    if (type == BAD_CHARACTER && isSpecialSymbols(getTokenText())) {
      type = getElementType(getTokenText());
    }

    return type;
  }

  @Override
  public MergeFunction getMergeFunction() {
    MergeFunction origMergeFunction = super.getMergeFunction();
    return (type, originalLexer) -> {
      if (type == BAD_CHARACTER && getElementType(getBaseTokenText()) == PyConsoleTokenTypes.PLING) {
        collectFullLine(originalLexer);
        return SHELL_COMMAND;
      }
      if (type == PERC) {
        if (additionMagicLineCheck(originalLexer)){
          collectFullLine(originalLexer);
          return MAGIC_COMMAND_LINE;
        }
      }
      return origMergeFunction.merge(type, originalLexer);
    };
  }

  private boolean additionMagicLineCheck(Lexer originalLexer) {
    if (getBaseTokenStart() == 0 &&
        originalLexer.getBufferEnd() >= 2 &&
        !StringUtil.isWhiteSpace(originalLexer.getBufferSequence().charAt(1))) {
      return true;
    }
    int lastIndex = myTokenQueue.size() - 1;
    if ((getBaseTokenStart() >= 1 &&
         originalLexer.getBufferEnd() > getBaseTokenStart() + 1 &&
         originalLexer.getBufferSequence().charAt(getBaseTokenStart() - 1) == '\n' &&
         !StringUtil.isWhiteSpace(originalLexer.getBufferSequence().charAt(getBaseTokenStart() + 1))) &&
        ((lastIndex >= 0 &&
          (myTokenQueue.get(lastIndex).getType() == STATEMENT_BREAK ||
           (myTokenQueue.get(lastIndex).getType() == END_OF_LINE_COMMENT)))
         || StringUtil.isEmptyOrSpaces(originalLexer.getBufferSequence().subSequence(0, getBaseTokenStart())))) {
      return true;
    }
    return false;
  }

  private static void collectFullLine(Lexer originalLexer) {
    while (originalLexer.getTokenType() != LINE_BREAK &&
           originalLexer.getTokenType() != STATEMENT_BREAK &&
           originalLexer.getTokenType() != null) {
      originalLexer.advance();
    }
  }

  public static PyElementType getElementType(String token) {
    return SPECIAL_IPYTHON_SYMBOLS.get(token);
  }

  public static boolean isSpecialSymbols(String token) {
    return SPECIAL_IPYTHON_SYMBOLS.containsKey(token);
  }
}
