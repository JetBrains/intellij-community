package com.intellij.refactoring.util.usageInfo;

import com.intellij.psi.*;
import com.intellij.usageView.UsageInfo;

/**
 * @author dsl
 */
public class DefaultConstructorImplicitUsageInfo extends UsageInfo {
  private final PsiMethod myOverridingConstructor;

  public DefaultConstructorImplicitUsageInfo(PsiMethod overridingConstructor) {
    super(overridingConstructor);
    myOverridingConstructor = overridingConstructor;
  }

  public PsiMethod getConstructor() {
    return myOverridingConstructor;
  }
}
