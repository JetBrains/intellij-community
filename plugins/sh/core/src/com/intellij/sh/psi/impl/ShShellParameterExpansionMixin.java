package com.intellij.sh.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.model.psi.PsiExternalReferenceHost;

public abstract class ShShellParameterExpansionMixin extends ShCompositeElementImpl implements PsiExternalReferenceHost {
  public ShShellParameterExpansionMixin(ASTNode node) {
    super(node);
  }
}