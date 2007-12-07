package com.intellij.psi.impl.light;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

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

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitReferenceList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public PsiElement copy() {
    return this;
  }

  @NotNull
  public PsiJavaCodeReferenceElement[] getReferenceElements() {
    return PsiJavaCodeReferenceElement.EMPTY_ARRAY;
  }

  @NotNull
  public PsiClassType[] getReferencedTypes() {
    return PsiClassType.EMPTY_ARRAY;
  }
}
