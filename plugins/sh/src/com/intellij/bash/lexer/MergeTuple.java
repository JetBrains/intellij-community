package com.intellij.bash.lexer;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

public class MergeTuple {
  private final TokenSet tokensToMerge;
  private final IElementType targetType;

  public static MergeTuple create(TokenSet tokensToMerge, IElementType targetType) {
    return new MergeTuple(tokensToMerge, targetType);
  }

  private MergeTuple(TokenSet tokensToMerge, IElementType targetType) {
    this.tokensToMerge = tokensToMerge;
    this.targetType = targetType;
  }

  public TokenSet getTokensToMerge() {
    return tokensToMerge;
  }

  public IElementType getTargetType() {
    return targetType;
  }
}
