/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.lexer;

import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * @author yole
 */
public class PythonHighlightingLexer extends PythonLexer {
  private final LanguageLevel myLanguageLevel;

  public PythonHighlightingLexer(LanguageLevel languageLevel) {
    myLanguageLevel = languageLevel;
    hasUnicodeImport = false;
  }

  static public IElementType convertStringType(IElementType tokenType, String tokenText,
                                        LanguageLevel languageLevel, boolean unicodeImport) {
    if (tokenType == PyTokenTypes.SINGLE_QUOTED_STRING) {
      if (languageLevel.isPy3K()) {
        if (!tokenText.toLowerCase().startsWith("b")) return PyTokenTypes.SINGLE_QUOTED_UNICODE;
      }
      else {
        if ((unicodeImport && !tokenText.toLowerCase().startsWith("b"))
            || tokenText.toLowerCase().startsWith("u")) return PyTokenTypes.SINGLE_QUOTED_UNICODE;
      }
    }
    if (tokenType == PyTokenTypes.TRIPLE_QUOTED_STRING) {
      if (languageLevel.isPy3K()) {
        if (!tokenText.toLowerCase().startsWith("b")) return PyTokenTypes.TRIPLE_QUOTED_UNICODE;
      }
      else {
        if ((unicodeImport && !tokenText.toLowerCase().startsWith("b"))
            || tokenText.toLowerCase().startsWith("u")) return PyTokenTypes.TRIPLE_QUOTED_UNICODE;
      }
    }
    return tokenType;
  }

  public IElementType convertStringType(IElementType tokenType, String tokenText) {
    return convertStringType(tokenType, tokenText, myLanguageLevel, hasUnicodeImport);
  }

  @Override
  public IElementType getTokenType() {
    final IElementType tokenType = super.getTokenType();

    if (PyTokenTypes.STRING_NODES.contains(tokenType)) {
      return convertStringType(tokenType, getTokenText());
    }

    if (tokenType == PyTokenTypes.IDENTIFIER) {
      final String tokenText = getTokenText();

      if (myLanguageLevel.hasWithStatement()) {
        if (tokenText.equals(PyNames.WITH)) return PyTokenTypes.WITH_KEYWORD;
        if (tokenText.equals(PyNames.AS)) return PyTokenTypes.AS_KEYWORD;
      }

      if (myLanguageLevel.hasPrintStatement()) {
        if (tokenText.equals(PyNames.PRINT)) return PyTokenTypes.PRINT_KEYWORD;
      }

      if (myLanguageLevel.isPy3K()) {
        if (tokenText.equals(PyNames.NONE)) return PyTokenTypes.NONE_KEYWORD;
        if (tokenText.equals(PyNames.TRUE)) return PyTokenTypes.TRUE_KEYWORD;
        if (tokenText.equals(PyNames.FALSE)) return PyTokenTypes.FALSE_KEYWORD;
        if (tokenText.equals(PyNames.NONLOCAL)) return PyTokenTypes.NONLOCAL_KEYWORD;
        if (tokenText.equals(PyNames.DEBUG)) return PyTokenTypes.DEBUG_KEYWORD;
        if (myLanguageLevel.isAtLeast(LanguageLevel.PYTHON37)) {
          if (tokenText.equals(PyNames.ASYNC)) return PyTokenTypes.ASYNC_KEYWORD;
          if (tokenText.equals(PyNames.AWAIT)) return PyTokenTypes.AWAIT_KEYWORD;
        }
      }
      else if (tokenText.equals(PyNames.EXEC)) {
        return PyTokenTypes.EXEC_KEYWORD;
      }
    }

    return tokenType;
  }

  private enum state {
    init,
    pending_future,
    pending_import,
    pending_lpar,
    pending_id,
    pending_comma,
    stop
  }

  private state myState = state.init;
  private boolean hasUnicodeImport = false;
  private int myImportOffset = -1;

  @Override
  public void advance() {
    IElementType type = super.getTokenType();
    switch (myState) {
      case init:
        if (type == PyTokenTypes.BACKSLASH) break;
        if (PyTokenTypes.WHITESPACE_OR_LINEBREAK.contains(type)) break;
        if (PyTokenTypes.END_OF_LINE_COMMENT == type) break;
        if (PyTokenTypes.DOCSTRING == type) break;
        if (type == PyTokenTypes.FROM_KEYWORD)
          myState = state.pending_future;
        else myState = state.stop;
        break;
      case pending_future:
        if (type == PyTokenTypes.BACKSLASH) break;
        if (PyTokenTypes.WHITESPACE_OR_LINEBREAK.contains(type)) break;
        if (type == PyTokenTypes.IDENTIFIER && PyNames.FUTURE_MODULE.equals(super.getTokenText()))
          myState = state.pending_import;
        else myState = state.stop;
        break;
      case pending_import:
        if (type == PyTokenTypes.BACKSLASH) break;
        if (PyTokenTypes.WHITESPACE_OR_LINEBREAK.contains(type)) break;
        if (type == PyTokenTypes.IMPORT_KEYWORD)
          myState = state.pending_lpar;
        else myState = state.stop;
        break;

      case pending_lpar:
        if (type == PyTokenTypes.LPAR) {
          myState = state.pending_id;
          break;
        }
      case pending_id:
        if (type == PyTokenTypes.BACKSLASH) break;
        if (PyTokenTypes.WHITESPACE_OR_LINEBREAK.contains(type)) break;
        if (type == PyTokenTypes.IDENTIFIER) {
          myState = state.pending_comma;
          if (PyNames.UNICODE_LITERALS.equals(super.getTokenText())) {
            hasUnicodeImport = true;
            myImportOffset = getTokenEnd();
          }
        }
        else myState = state.init;
        break;
      case pending_comma:
        if (type == PyTokenTypes.RPAR) break;
        if (type == PyTokenTypes.BACKSLASH) break;
        if (PyTokenTypes.LINE_BREAK == type) myState = state.init;
        if (PyTokenTypes.WHITESPACE_OR_LINEBREAK.contains(type)) break;
        if (type == PyTokenTypes.COMMA)
          myState = state.pending_id;
        break;
      case stop:
        break;
    }
    super.advance();
  }


  public int getImportOffset() {
    return myImportOffset;
  }

  public void clearState(int position) {
    myState = state.init;
    myImportOffset = position;
    hasUnicodeImport = false;
  }

}
