package com.intellij.sh.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.sh.psi.ShCompositeElement;
import com.intellij.sh.psi.ShFunctionDefinition;
import com.intellij.util.text.TextRangeUtil;
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
    return lool(this, place.getTextRange(), processor, state);
  }

  private boolean lool(@Nullable PsiElement element, TextRange lastParent, @NotNull PsiScopeProcessor processor, @NotNull ResolveState state) {
    if (element == null) return true;
    for (PsiElement e = element; e != null; e = e.getPrevSibling()) {
      if (!e.getTextRange().contains(lastParent) && e.getTextRange().getEndOffset() > lastParent.getStartOffset()) continue;
      if (!processor.execute(e, state) || (shouldGoDeeper(e) && !lool(e.getLastChild(), lastParent, processor, state))) return false;
    }
    return true;
  }

  private static boolean shouldGoDeeper(@NotNull PsiElement element) {
      return !(element instanceof ShFunctionDefinition);
  }

  @Override
  public String toString() {
    return getNode().getElementType().toString();
  }
}
