package com.intellij.psi.impl.light;

import com.intellij.psi.*;

/**
 * @author max
 */
public class LightEmptyImplementsList extends LightElement implements PsiReferenceList {
  public LightEmptyImplementsList(PsiManager manager) {
    super(manager);
  }

  public String toString() {
    return "PsiReferenceList";
  }

  public String getText() {
    return "";
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitReferenceList(this);
  }

  public PsiElement copy() {
    return this;
  }

  public PsiJavaCodeReferenceElement[] getReferenceElements() {
    return PsiJavaCodeReferenceElement.EMPTY_ARRAY;
  }

  public PsiClassType[] getReferencedTypes() {
    return PsiClassType.EMPTY_ARRAY;
  }
}
