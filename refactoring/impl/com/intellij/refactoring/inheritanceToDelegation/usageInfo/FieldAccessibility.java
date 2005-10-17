package com.intellij.refactoring.inheritanceToDelegation.usageInfo;

import com.intellij.psi.*;

/**
 * @author dsl
 */
public class FieldAccessibility {
  public static final FieldAccessibility INVISIBLE = new FieldAccessibility(false, null);

  private final boolean myIsVisible;
  private final PsiClass myContainingClass;

  public FieldAccessibility(boolean visible, PsiClass containingClass) {
    myIsVisible = visible;
    myContainingClass = containingClass;
  }

  public boolean isAccessible() {
    return myIsVisible;
  }

  public PsiClass getContainingClass() {
    return myContainingClass;
  }
}
