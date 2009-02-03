package com.intellij.refactoring.util.usageInfo;

import com.intellij.psi.*;
import com.intellij.usageView.UsageInfo;

/**
 * @author dsl
 */
public class NoConstructorClassUsageInfo extends UsageInfo {
  private final PsiClass myClass;

  public NoConstructorClassUsageInfo(PsiClass aClass) {
    super(aClass);
    myClass = aClass;
  }

  public PsiClass getPsiClass() {
    return myClass;
  }
}
