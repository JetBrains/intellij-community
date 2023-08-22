// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh;

import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.sh.lexer.ShTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ShBraceMatcher implements PairedBraceMatcher, ShTokenTypes {
  private static final BracePair[] PAIRS = new BracePair[]{
      new BracePair(INPUT_PROCESS_SUBSTITUTION, RIGHT_PAREN, true),
      new BracePair(OUTPUT_PROCESS_SUBSTITUTION, RIGHT_PAREN, true),
      new BracePair(LEFT_PAREN, RIGHT_PAREN, true),
      new BracePair(LEFT_SQUARE, RIGHT_SQUARE, false),
      new BracePair(LEFT_DOUBLE_BRACKET, RIGHT_DOUBLE_BRACKET, false),
      new BracePair(EXPR_CONDITIONAL_LEFT, EXPR_CONDITIONAL_RIGHT, false),
      new BracePair(HEREDOC_MARKER_START, HEREDOC_MARKER_END, false),
      new BracePair(DO, DONE, true),
      new BracePair(IF, FI, true),
      new BracePair(CASE, ESAC, true),
      new BracePair(LEFT_CURLY, RIGHT_CURLY, true),
  };

  @Override
  public BracePair @NotNull [] getPairs() {
    return PAIRS;
  }

  @Override
  public boolean isPairedBracesAllowedBeforeType(@NotNull final IElementType lbraceType, @Nullable final IElementType tokenType) {
    return true;
  }

  @Override
  public int getCodeConstructStart(final PsiFile file, int openingBraceOffset) {
    return openingBraceOffset;
  }
}
