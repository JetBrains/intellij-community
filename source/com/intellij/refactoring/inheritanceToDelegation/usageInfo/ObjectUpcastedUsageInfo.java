package com.intellij.refactoring.inheritanceToDelegation.usageInfo;

import com.intellij.psi.*;

/**
 * @author dsl
 */
public class ObjectUpcastedUsageInfo extends UpcastedUsageInfo {
  public ObjectUpcastedUsageInfo(PsiElement element, FieldAccessibility delegateFieldVisibility) {
    super(element, null, delegateFieldVisibility);
  }
}
