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
import com.jetbrains.python.psi.FutureFeature;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyStringLiteralCoreUtil;
import com.jetbrains.python.psi.PyStringLiteralUtil;
import org.jetbrains.annotations.NotNull;


public class PythonHighlightingLexer extends PythonLexer {
  private final LanguageLevel myLanguageLevel;

  public PythonHighlightingLexer(LanguageLevel languageLevel) {
    this(languageLevel, PythonLexerKind.REGULAR);
  }

  public PythonHighlightingLexer(LanguageLevel languageLevel, @NotNull PythonLexerKind kind) {
    super(kind);
    myLanguageLevel = languageLevel;
    hasUnicodeImport = false;
    hasPrintFunctionImport = false;
  }

  @NotNull
  public static IElementType convertStringType(@NotNull IElementType tokenType,
                                               @NotNull String tokenText,
                                               @NotNull LanguageLevel languageLevel,
                                               boolean unicodeImport) {
    final String prefix = PyStringLiteralCoreUtil.getPrefix(tokenText);

    if (tokenType == PyTokenTypes.SINGLE_QUOTED_STRING) {
      if (languageLevel.isPy3K()) {
        if (!PyStringLiteralUtil.isBytesPrefix(prefix)) return PyTokenTypes.SINGLE_QUOTED_UNICODE;
      }
      else if (unicodeImport && !PyStringLiteralUtil.isBytesPrefix(prefix) || PyStringLiteralUtil.isUnicodePrefix(prefix)) {
        return PyTokenTypes.SINGLE_QUOTED_UNICODE;
      }
    }
    if (tokenType == PyTokenTypes.TRIPLE_QUOTED_STRING) {
      if (languageLevel.isPy3K()) {
        if (!PyStringLiteralUtil.isBytesPrefix(prefix)) return PyTokenTypes.TRIPLE_QUOTED_UNICODE;
      }
      else if (unicodeImport && !PyStringLiteralUtil.isBytesPrefix(prefix) || PyStringLiteralUtil.isUnicodePrefix(prefix)) {
        return PyTokenTypes.TRIPLE_QUOTED_UNICODE;
      }
    }
    return tokenType;
  }

  @Override
  public IElementType getTokenType() {
    final IElementType tokenType = super.getTokenType();

    if (PyTokenTypes.STRING_NODES.contains(tokenType)) {
      return convertStringType(tokenType, getTokenText(), myLanguageLevel, hasUnicodeImport);
    }

    if (tokenType == PyTokenTypes.IDENTIFIER) {
      final String tokenText = getTokenText();

      if (tokenText.equals(PyNames.WITH)) return PyTokenTypes.WITH_KEYWORD;
      if (tokenText.equals(PyNames.AS)) return PyTokenTypes.AS_KEYWORD;

      if (myLanguageLevel.hasPrintStatement() && !hasPrintFunctionImport) {
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
  private boolean hasUnicodeImport;
  private int myImportOffset = -1;
  private boolean hasPrintFunctionImport;

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
          } else if (FutureFeature.PRINT_FUNCTION.toString().equals(super.getTokenText())) {
            hasPrintFunctionImport = true;
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
    hasPrintFunctionImport = false;
  }

}
