package com.intellij.sh.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.sh.ShTypes.LEFT_CURLY;
import static com.intellij.sh.ShTypes.RIGHT_CURLY;

public class ShLazyBlockImpl extends ShLazyParseablePsiElement {
  public ShLazyBlockImpl(@NotNull IElementType type, @Nullable CharSequence buffer) {
    super(type, buffer);
  }

  ShLazyBlockImpl(ASTNode node) {
    super(node.getElementType(), node.getText());
  }

  @NotNull
  public PsiElement getLeftCurly() {
    return findNotNullChildByType(LEFT_CURLY);
  }

  @Nullable
  public PsiElement getRightCurly() {
    return findChildByElementType(RIGHT_CURLY);
  }
}
