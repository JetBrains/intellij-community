package com.intellij.refactoring.inheritanceToDelegation.usageInfo;

import com.intellij.psi.*;

/**
 * @author dsl
 */
public class UpcastedUsageInfo extends InheritanceToDelegationUsageInfo{
  public final PsiClass upcastedTo;

  public UpcastedUsageInfo(PsiElement element, PsiClass upcastedTo, FieldAccessibility delegateFieldVisibility) {
    super(element, delegateFieldVisibility);
    this.upcastedTo = upcastedTo;
  }
}
