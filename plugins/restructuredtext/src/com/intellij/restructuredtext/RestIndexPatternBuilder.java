// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.restructuredtext;

import com.intellij.lexer.Lexer;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.search.IndexPatternBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.restructuredtext.lexer.RestFlexLexer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class RestIndexPatternBuilder implements IndexPatternBuilder {
  public static final TokenSet COMMENTS = TokenSet.create(RestTokenTypes.COMMENT);

  @Override
  public @Nullable Lexer getIndexingLexer(@NotNull PsiFile file) {
    if (file instanceof RestFile) {
      return new RestFlexLexer();
    }
    return null;
  }

  @Override
  public @Nullable TokenSet getCommentTokenSet(@NotNull PsiFile file) {
    if (file instanceof RestFile) {
      return COMMENTS;
    }
    return null;
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
