package com.intellij.psi.impl.light;

import com.intellij.psi.*;

/**
 *  @author dsl
 */
public abstract class ImplicitVariableImpl extends LightVariableBase implements ImplicitVariable {

  public ImplicitVariableImpl(PsiManager manager, PsiIdentifier nameIdentifier, PsiType type, boolean writable, PsiElement scope) {
    super(manager, nameIdentifier, type, writable, scope);
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitImplicitVariable(this);
  }

  public String toString() {
    return "Implicit variable:" + getName();
  }
}
