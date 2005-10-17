package com.intellij.refactoring.convertToInstanceMethod;

import com.intellij.usageView.UsageInfo;
import com.intellij.psi.PsiReference;

/**
 * @author dsl
 */
class JavaDocUsageInfo extends UsageInfo {
  private final PsiReference myReference;
  JavaDocUsageInfo(PsiReference ref) {
    super(ref.getElement());
    myReference = ref;
  }

  public PsiReference getReference() {
    return myReference;
  }
}
