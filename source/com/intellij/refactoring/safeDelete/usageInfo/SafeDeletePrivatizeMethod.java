package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.psi.PsiMethod;

/**
 * @author dsl
 */
public class SafeDeletePrivatizeMethod extends SafeDeleteUsageInfo {
  public SafeDeletePrivatizeMethod(PsiMethod method, PsiMethod overridenMethod) {
    super(method, overridenMethod);
  }

  public PsiMethod getMethod() {
    return (PsiMethod) getElement();
  }
}
