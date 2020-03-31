// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console;

import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.highlighting.PyHighlighter;

class PyConsoleSourceHighlighter {
  private final Lexer myLexer;

  private final EditorColorsScheme myScheme;
  private final PyHighlighter myPyHighlighter;

  private int myLexerState;
  private final PythonConsoleView myPythonConsoleView;

  PyConsoleSourceHighlighter(PythonConsoleView pythonConsoleView, EditorColorsScheme scheme, PyHighlighter pyHighlighter) {
    myPythonConsoleView = pythonConsoleView;
    myScheme = scheme;
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
    Pair<String, ConsoleViewContentType> res = Pair.create(
      myLexer.getTokenText(),
      tokenType == null ? ConsoleViewContentType.NORMAL_OUTPUT : ConsoleViewUtil.getContentTypeForToken(tokenType, myPyHighlighter));
    myLexer.advance();

    return res;
  }

  private int getLexerState() {
    return myLexerState != 1024 ? myLexerState : 0;
  }

  protected TextAttributes convertAttributes(TextAttributesKey[] keys) {
    EditorColorsScheme scheme = myScheme;
    TextAttributes attrs = scheme.getAttributes(HighlighterColors.TEXT);
    for (TextAttributesKey key : keys) {
      TextAttributes attrs2 = scheme.getAttributes(key);
      if (attrs2 != null) {
        attrs = TextAttributes.merge(attrs, attrs2);
      }
    }
    return attrs;
  }
}
