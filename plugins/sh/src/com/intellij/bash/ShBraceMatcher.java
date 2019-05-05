package com.intellij.bash;

import com.intellij.bash.lexer.ShTokenTypes;
import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShBraceMatcher implements PairedBraceMatcher, ShTokenTypes {
  private static final BracePair[] PAIRS = new BracePair[]{
      new BracePair(LEFT_PAREN, RIGHT_PAREN, true),
      new BracePair(LEFT_SQUARE, RIGHT_SQUARE, false),
      new BracePair(LEFT_DOUBLE_PAREN, RIGHT_DOUBLE_PAREN, true),
      new BracePair(LEFT_DOUBLE_BRACKET, RIGHT_DOUBLE_BRACKET, false),
      new BracePair(EXPR_CONDITIONAL_LEFT, EXPR_CONDITIONAL_RIGHT, false),
      new BracePair(HEREDOC_MARKER_START, HEREDOC_MARKER_END, false),
      new BracePair(DO, DONE, true),
      new BracePair(IF, FI, true),
      new BracePair(CASE, ESAC, true),
      new BracePair(LEFT_CURLY, RIGHT_CURLY, true),
  };

  @NotNull
  public BracePair[] getPairs() {
    return PAIRS;
  }

  public boolean isPairedBracesAllowedBeforeType(@NotNull final IElementType lbraceType, @Nullable final IElementType tokenType) {
    return true;
  }

  public int getCodeConstructStart(final PsiFile file, int openingBraceOffset) {
    return openingBraceOffset;
  }
}
