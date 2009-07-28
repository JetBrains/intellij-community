package com.intellij.psi;

/**
 * Used in Generify refactoring
 */
public abstract class PsiTypeVariable extends PsiType {
  protected PsiTypeVariable() {
    super(PsiAnnotation.EMPTY_ARRAY);
  }

  public abstract int getIndex();
  public abstract boolean isValidInContext (PsiType type);

  public <A> A accept(PsiTypeVisitor<A> visitor) {
    if (visitor instanceof PsiTypeVisitorEx) {
      return ((PsiTypeVisitorEx<A>)visitor).visitTypeVariable(this);
    }

    return visitor.visitType(this);
  }
}
