/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python;

import com.intellij.lexer.Lexer;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.cache.impl.BaseFilterLexer;
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

/**
 * @author traff
 */
public class PyFilterLexer extends BaseFilterLexer {
  protected PyFilterLexer(Lexer originalLexer, OccurrenceConsumer occurrenceConsumer) {
    super(originalLexer, occurrenceConsumer);
  }

  private static final TokenSet ourSkipWordsScanSet = TokenSet.orSet(
    TokenSet.create(
      TokenType.WHITE_SPACE,
      PyTokenTypes.LPAR,
      PyTokenTypes.RPAR,
      PyTokenTypes.LBRACE,
      PyTokenTypes.RBRACE,
      PyTokenTypes.LBRACKET,
      PyTokenTypes.RBRACKET,
      PyTokenTypes.SEMICOLON,
      PyTokenTypes.COMMA,
      PyTokenTypes.DOT,
      PyTokenTypes.AT
    ),
    PyTokenTypes.OPERATIONS
  );

  @Override
  public void advance() {
    final IElementType tokenType = myDelegate.getTokenType();

    if (tokenType == PyTokenTypes.IDENTIFIER
        || PyTokenTypes.SCALAR_LITERALS.contains(tokenType)) {
      addOccurrenceInToken(UsageSearchContext.IN_CODE);
    }
    else if (PyTokenTypes.STRING_NODES.contains(tokenType)) {
      scanWordsInToken(UsageSearchContext.IN_STRINGS | UsageSearchContext.IN_FOREIGN_LANGUAGES, false, true);
    }
    else if (PyIndexPatternBuilder.COMMENTS.contains(tokenType)) {
      scanWordsInToken(UsageSearchContext.IN_COMMENTS, false, false);
      advanceTodoItemCountsInToken();
    }
    else if (!ourSkipWordsScanSet.contains(tokenType)) {
      scanWordsInToken(UsageSearchContext.IN_PLAIN_TEXT, false, false);
    }


    myDelegate.advance();
  }
}
