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
package com.jetbrains.python.documentation.doctest;

import com.intellij.psi.tree.IElementType;
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
          super.advance();
        }
      }
    }
    else if (super.getTokenType() == PyTokenTypes.GTGT) {
      advanceBase();
      if (super.getTokenType() == PyTokenTypes.GT) {
        advanceBase();
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

    while (getBaseTokenType() != null && (PyTokenTypes.WHITESPACE_OR_LINEBREAK.contains(getBaseTokenType()) ||
           ourIgnoreSet.contains(getBaseTokenType()))) {
      if (getBaseTokenType() == PyTokenTypes.TAB) {
        indent = ((indent / 8) + 1) * 8;
      }
      else if (getBaseTokenType() == PyTokenTypes.SPACE) {
        indent++;
      }
      else if (getBaseTokenType() == PyTokenTypes.LINE_BREAK) {
        indent = 0;
        super.getNextLineIndent();
      }

      advanceBase();
    }

    if (getBaseTokenType() == null) {
      return 0;
    }
    return indent > 0? indent - 1 : indent;
  }

  protected void checkSignificantTokens() {
    IElementType tokenType = getBaseTokenType();
    if (!PyTokenTypes.WHITESPACE_OR_LINEBREAK.contains(tokenType) && tokenType != getCommentTokenType() &&
      ! ourIgnoreSet.contains(tokenType)) {
      myLineHasSignificantTokens = true;
    }
  }
  @Override
  protected void checkStartState(int startOffset, int initialState) {
  }
}
