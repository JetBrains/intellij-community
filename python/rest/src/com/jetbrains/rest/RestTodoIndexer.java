// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.rest;

import com.intellij.lexer.Lexer;
import com.intellij.psi.impl.cache.impl.BaseFilterLexer;
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer;
import com.intellij.psi.impl.cache.impl.todo.LexerBasedTodoIndexer;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.rest.lexer.RestFlexLexer;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class RestTodoIndexer extends LexerBasedTodoIndexer {
  @NotNull
  @Override
  public Lexer createLexer(@NotNull OccurrenceConsumer consumer) {
    return new BaseFilterLexer(new RestFlexLexer(), consumer) {
      @Override
      public void advance() {
        final IElementType tokenType = myDelegate.getTokenType();

        if (RestTokenTypes.COMMENT == tokenType) {
          scanWordsInToken(UsageSearchContext.IN_COMMENTS, false, false);
          advanceTodoItemCountsInToken();
        }

        myDelegate.advance();
      }
    };
  }
}
