// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText;

import com.intellij.lexer.Lexer;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.search.IndexPatternBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.python.reStructuredText.lexer.RestFlexLexer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RestIndexPatternBuilder implements IndexPatternBuilder {
  public static final TokenSet COMMENTS = TokenSet.create(RestTokenTypes.COMMENT);

  @Nullable
  @Override
  public Lexer getIndexingLexer(@NotNull PsiFile file) {
    if (file instanceof RestFile) {
      return new RestFlexLexer();
    }
    return null;
  }

  @Nullable
  @Override
  public TokenSet getCommentTokenSet(@NotNull PsiFile file) {
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
