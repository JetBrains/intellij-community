package com.intellij.bash.psi.impl;

import com.intellij.bash.psi.BashCompositeElement;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.usageView.UsageViewUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class BashCompositeElementImpl extends ASTWrapperPsiElement implements BashCompositeElement {
  public BashCompositeElementImpl(ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return getNode().getElementType().toString();
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    return processDeclarations(this, processor, state, lastParent, place);
  }

  static boolean processDeclarations(@NotNull PsiElement element, @NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    return processor.execute(element, state) && ResolveUtil.processChildren(element, processor, state, lastParent, place);
  }

  @Override
  public ItemPresentation getPresentation() {
    final String text = UsageViewUtil.createNodeText(this);
    return new ItemPresentation() {
      @NotNull
      @Override
      public String getPresentableText() {
        return text;
      }

      @NotNull
      @Override
      public String getLocationString() {
        return getContainingFile().getName();
      }

      @Nullable
      @Override
      public Icon getIcon(boolean b) {
        return BashCompositeElementImpl.this.getIcon(0);
      }
    };
  }
}
