// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.highlighting;

import com.intellij.lexer.Lexer;
import com.intellij.psi.impl.cache.impl.BaseFilterLexer;
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer;
import com.intellij.psi.impl.cache.impl.todo.LexerBasedTodoIndexer;
import com.intellij.psi.search.UsageSearchContext;
import org.jetbrains.annotations.NotNull;

public class MarkdownTodoIndexer extends LexerBasedTodoIndexer {
  @Override
  public @NotNull Lexer createLexer(@NotNull OccurrenceConsumer consumer) {
    return new BaseFilterLexer(new MarkdownHighlightingLexer(), consumer) {
      @Override
      public void advance() {
        if (MarkdownIndexPatternBuilder.COMMENT_TOKEN_SET.contains(myDelegate.getTokenType())) {
          scanWordsInToken(UsageSearchContext.IN_COMMENTS, false, false);
          advanceTodoItemCountsInToken();
        }

        myDelegate.advance();
      }
    };
  }
}