package com.intellij.refactoring.turnRefsToSuper;

import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;

/**
 * @author dsl
 */
public class TurnToSuperReferenceUsageInfo extends UsageInfo {
  public TurnToSuperReferenceUsageInfo(PsiElement element) {
    super(element);
  }
}
