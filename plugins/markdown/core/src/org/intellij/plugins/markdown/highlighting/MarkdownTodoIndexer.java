// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.highlighting;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.impl.cache.impl.BaseFilterLexer;
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer;
import com.intellij.psi.impl.cache.impl.todo.LexerBasedTodoIndexer;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public class MarkdownTodoIndexer extends LexerBasedTodoIndexer {
  private static final NotNullLazyValue<TokenSet> HTML_COMMENT_TOKENS = NotNullLazyValue.atomicLazy(() -> {
    Language html = Language.findLanguageByID("HTML");
    if (html == null) return TokenSet.EMPTY;
    ParserDefinition def = LanguageParserDefinitions.INSTANCE.forLanguage(html);
    return def == null ? TokenSet.EMPTY : def.getCommentTokens();
  });

  @Override
  public @NotNull Lexer createLexer(@NotNull OccurrenceConsumer consumer) {
    return new BaseFilterLexer(new MarkdownHighlightingLexer(), consumer) {
      @Override
      public void advance() {
        IElementType tokenType = myDelegate.getTokenType();
        if (MarkdownIndexPatternBuilder.COMMENT_TOKEN_SET.contains(tokenType)
            || HTML_COMMENT_TOKENS.getValue().contains(tokenType)) {
          scanWordsInToken(UsageSearchContext.IN_COMMENTS, false, false);
          advanceTodoItemCountsInToken();
        }

        myDelegate.advance();
      }
    };
  }
}