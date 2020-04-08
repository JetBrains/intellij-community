package com.intellij.sh.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.sh.psi.ResolveUtil;
import com.intellij.sh.psi.ShCompositeElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ShLazyParseablePsiElement  extends LazyParseablePsiElement implements ShCompositeElement {
  ShLazyParseablePsiElement(@NotNull IElementType type, @Nullable CharSequence buffer) {
    super(type, buffer);
  }

  @NotNull
  PsiElement findNotNullChildByType(IElementType type) {
    ASTNode node = getNode().findChildByType(type);
    PsiElement psi = node != null ? node.getPsi() : null;
    assert psi != null : getText() + "\n parent=" + getParent().getText();
    return psi;
  }

  @Nullable
  PsiElement findChildByElementType(IElementType type) {
    ASTNode node = getNode().findChildByType(type);
    return node == null ? null : node.getPsi();
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return ResolveUtil.processChildren(this, processor, state, lastParent, place);
  }

  @Override
  public String toString() {
    return getNode().getElementType().toString();
  }
}
