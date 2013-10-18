package com.jetbrains.python;

import com.intellij.lang.PairedBraceMatcher;
import com.intellij.lang.BracePair;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiFile;
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

  public BracePair[] getPairs() {
    return PAIRS;
  }

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

  public int getCodeConstructStart(final PsiFile file, int openingBraceOffset) {
    return openingBraceOffset;
  }
}
