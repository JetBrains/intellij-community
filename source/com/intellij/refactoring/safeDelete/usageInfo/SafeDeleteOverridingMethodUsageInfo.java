package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.psi.PsiMethod;

/**
 * @author dsl
 */
public class SafeDeleteOverridingMethodUsageInfo extends SafeDeleteUsageInfo {

  public SafeDeleteOverridingMethodUsageInfo(PsiMethod overridingMethod, PsiMethod method) {
    super(overridingMethod, method);
  }

  public PsiMethod getOverridingMethod() {
    return (PsiMethod) getElement();
  }

  public PsiMethod getReferencedMethod() {
    return (PsiMethod) getReferencedElement();
  }
}
