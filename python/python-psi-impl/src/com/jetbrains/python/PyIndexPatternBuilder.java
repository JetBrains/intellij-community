// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.lexer.Lexer;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.search.IndexPatternBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.lexer.PythonLexer;
import com.jetbrains.python.psi.PyFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PyIndexPatternBuilder implements IndexPatternBuilder {
  public static final TokenSet COMMENTS = TokenSet.create(PyTokenTypes.END_OF_LINE_COMMENT, PyTokenTypes.DOCSTRING);

  @Nullable
  @Override
  public Lexer getIndexingLexer(@NotNull PsiFile file) {
    if (file instanceof PyFile) {
      return new PythonLexer();
    }
    return null;
  }

  @Nullable
  @Override
  public TokenSet getCommentTokenSet(@NotNull PsiFile file) {
    if (file instanceof PyFile) {
      return COMMENTS;
    }
    return null;
  }

  @Override
  public int getCommentStartDelta(IElementType tokenType) {
    return tokenType == PyTokenTypes.END_OF_LINE_COMMENT
           ? 1
           : tokenType == PyTokenTypes.DOCSTRING
             ? 3
             : 0;
  }

  @Override
  public int getCommentEndDelta(IElementType tokenType) {
    return tokenType == PyTokenTypes.DOCSTRING ? 3 : 0;
  }

  @NotNull
  @Override
  public String getCharsAllowedInContinuationPrefix(@NotNull IElementType tokenType) {
    return tokenType == PyTokenTypes.END_OF_LINE_COMMENT ? "#" : "";
  }
}
