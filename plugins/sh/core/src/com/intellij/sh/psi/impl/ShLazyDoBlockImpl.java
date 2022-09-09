package com.intellij.sh.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.sh.ShTypes.DO;
import static com.intellij.sh.ShTypes.DONE;

public class ShLazyDoBlockImpl extends ShLazyParseablePsiElement {
  public ShLazyDoBlockImpl(@NotNull IElementType type, @Nullable CharSequence buffer) {
    super(type, buffer);
  }

  ShLazyDoBlockImpl(ASTNode node) {
    super(node.getElementType(), node.getText());
  }

  @NotNull
  public PsiElement getDo() {
    return findNotNullChildByType(DO);
  }

  @Nullable
  public PsiElement getDone() {
    return findChildByElementType(DONE);
  }
}
