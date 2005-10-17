package com.intellij.refactoring.makeStatic;

import com.intellij.psi.PsiMethod;
import com.intellij.usageView.UsageInfo;

/**
 *  @author dsl
 */
public class OverridingMethodUsageInfo extends UsageInfo {
  public OverridingMethodUsageInfo(PsiMethod overridingMethod) {
    super(overridingMethod);
  }
}
