// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console;

import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.highlighting.PyHighlighter;

final class PyConsoleSourceHighlighter {
  private final Lexer myLexer;

  private final PyHighlighter myPyHighlighter;

  private int myLexerState;
  private final PythonConsoleView myPythonConsoleView;

  PyConsoleSourceHighlighter(PythonConsoleView pythonConsoleView, PyHighlighter pyHighlighter) {
    myPythonConsoleView = pythonConsoleView;
    myPyHighlighter = pyHighlighter;
    myLexer = myPyHighlighter.getHighlightingLexer();
  }

  public void printHighlightedSource(String text) {
    myLexer.start(text, 0, text.length(), getLexerState());
    while (hasNext()) {
      Pair<String, ConsoleViewContentType> pair = next();
      myPythonConsoleView.printText(pair.first, pair.second);
    }
  }

  private boolean hasNext() {
    return myLexer.getTokenType() != null;
  }

  private Pair<String, ConsoleViewContentType> next() {
    myLexerState = myLexer.getState();
    IElementType tokenType = myLexer.getTokenType();
    Pair<String, ConsoleViewContentType> res = new Pair<>(
      myLexer.getTokenText(),
      tokenType == null ? ConsoleViewContentType.NORMAL_OUTPUT : ConsoleViewUtil.getContentTypeForToken(tokenType, myPyHighlighter)
    );
    myLexer.advance();

    return res;
  }

  private int getLexerState() {
    return myLexerState != 1024 ? myLexerState : 0;
  }
}
