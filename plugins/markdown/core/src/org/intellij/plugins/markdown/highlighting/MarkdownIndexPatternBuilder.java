// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.highlighting;

import com.intellij.lexer.LayeredLexer;
import com.intellij.lexer.Lexer;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.search.IndexPatternBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.intellij.plugins.markdown.lang.MarkdownElementTypes;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MarkdownIndexPatternBuilder implements IndexPatternBuilder {
  public static final TokenSet COMMENT_TOKEN_SET = TokenSet.create(MarkdownElementTypes.COMMENT_VALUE);

  @Override
  public @Nullable Lexer getIndexingLexer(@NotNull PsiFile file) {
    if (!(file instanceof MarkdownFile)) {
      return null;
    }

    try {
      LayeredLexer.ourDisableLayersFlag.set(Boolean.TRUE);
      return ((MarkdownFile)file).getParserDefinition().createLexer(file.getProject());
    }
    finally {
      LayeredLexer.ourDisableLayersFlag.remove();
    }
  }

  @Override
  public @Nullable TokenSet getCommentTokenSet(@NotNull PsiFile file) {
    return file instanceof MarkdownFile ? COMMENT_TOKEN_SET : null;
  }

  @Override
  public int getCommentStartDelta(IElementType tokenType) {
    return 0;
  }

  @Override
  public int getCommentEndDelta(IElementType tokenType) {
    return 0;
  }
}
