// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyBraceMatcher implements PairedBraceMatcher {
  private final BracePair[] PAIRS;

  public PyBraceMatcher() {
    PAIRS = new BracePair[]{new BracePair(PyTokenTypes.LPAR, PyTokenTypes.RPAR, false),
      new BracePair(PyTokenTypes.LBRACKET, PyTokenTypes.RBRACKET, false), new BracePair(PyTokenTypes.LBRACE, PyTokenTypes.RBRACE, false)};
  }

  @Override
  @NotNull
  public BracePair[] getPairs() {
    return PAIRS;
  }

  @Override
  public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType, @Nullable IElementType contextType) {
    return
      PyTokenTypes.WHITESPACE_OR_LINEBREAK.contains(contextType) ||
      contextType == PyTokenTypes.END_OF_LINE_COMMENT ||
      contextType == PyTokenTypes.COLON ||
      contextType == PyTokenTypes.COMMA ||
      contextType == PyTokenTypes.RPAR ||
      contextType == PyTokenTypes.RBRACKET ||
      contextType == PyTokenTypes.RBRACE ||
      contextType == PyTokenTypes.LBRACE ||
      contextType == null;
  }

  @Override
  public int getCodeConstructStart(final PsiFile file, int openingBraceOffset) {
    return openingBraceOffset;
  }
}
