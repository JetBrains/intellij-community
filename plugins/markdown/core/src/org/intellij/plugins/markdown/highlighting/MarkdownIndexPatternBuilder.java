// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.highlighting;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lexer.LayeredLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.search.IndexPatternBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.intellij.plugins.markdown.lang.MarkdownElementTypes;
import org.intellij.plugins.markdown.lang.MarkdownLanguageUtilsKt;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MarkdownIndexPatternBuilder implements IndexPatternBuilder {
  public static final TokenSet COMMENT_TOKEN_SET = TokenSet.create(
    MarkdownElementTypes.COMMENT_VALUE
  );

  static final NotNullLazyValue<TokenSet> HTML_COMMENT_TOKENS = NotNullLazyValue.atomicLazy(() -> {
    Language html = Language.findLanguageByID("HTML");
    if (html == null) return TokenSet.EMPTY;
    ParserDefinition def = LanguageParserDefinitions.INSTANCE.forLanguage(html);
    return def == null ? TokenSet.EMPTY : def.getCommentTokens();
  });

  @Override
  public @Nullable Lexer getIndexingLexer(@NotNull PsiFile file) {
    if (isMarkdownEmbeddedHtmlFile(file)) {
      return new MarkdownHighlightingLexer();
    }

    if (!(file instanceof MarkdownFile markdownFile)) {
      return null;
    }
    
    try {
      LayeredLexer.ourDisableLayersFlag.set(Boolean.TRUE);
      return markdownFile.getParserDefinition().createLexer(markdownFile.getProject());
    }
    finally {
      LayeredLexer.ourDisableLayersFlag.remove();
    }
  }

  @Override
  public @Nullable TokenSet getCommentTokenSet(@NotNull PsiFile file) {
    if (file instanceof MarkdownFile) {
      return COMMENT_TOKEN_SET;
    }
    return isMarkdownEmbeddedHtmlFile(file) ? HTML_COMMENT_TOKENS.getValue() : null;
  }

  @Override
  public int getCommentStartDelta(IElementType tokenType) {
    return 0;
  }

  @Override
  public int getCommentEndDelta(IElementType tokenType) {
    return 0;
  }

  private static boolean isMarkdownEmbeddedHtmlFile(@NotNull PsiFile file) {
    return !(file instanceof MarkdownFile)
           && MarkdownLanguageUtilsKt.isMarkdownLanguage(file.getViewProvider().getBaseLanguage());
  }
}
