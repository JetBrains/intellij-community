package com.intellij.psi;

/**
 * @author ven
 */
public class PsiTypeVisitorEx<A> extends PsiTypeVisitor<A> {
  public A visitTypeVariable(PsiTypeVariable var) {
    return visitType(var);
  }

  public A visitBottom (Bottom bottom) {
    return visitType(bottom);
  }
}
