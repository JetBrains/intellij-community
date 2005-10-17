package com.intellij.refactoring.util.usageInfo;

import com.intellij.psi.*;
import com.intellij.usageView.UsageInfo;

/**
 * @author dsl
 */
public class DefaultConstructorImplicitUsageInfo extends UsageInfo {
  private final PsiMethod myOverridingConstructor;
  private final PsiMethod myBaseConstructor;

  public DefaultConstructorImplicitUsageInfo(PsiMethod overridingConstructor, PsiMethod baseConstructor) {
    super(overridingConstructor);
    myOverridingConstructor = overridingConstructor;
    myBaseConstructor = baseConstructor;
  }

  public PsiMethod getConstructor() {
    return myOverridingConstructor;
  }

  public PsiMethod getBaseConstructor() {
    return myBaseConstructor;
  }
}
