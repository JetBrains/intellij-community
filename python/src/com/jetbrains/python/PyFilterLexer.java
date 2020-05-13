// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.lexer.Lexer;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.cache.impl.BaseFilterLexer;
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

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
      if (tokenType == PyTokenTypes.DOCSTRING) {
        advanceTodoItemCountsInToken();
      }
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
