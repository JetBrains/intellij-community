package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public class SafeDeleteUsageInfo extends UsageInfo {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteUsageInfo");
  private final PsiElement myReferencedElement;

  public SafeDeleteUsageInfo(@NotNull PsiElement element, PsiElement referencedElement) {
    super(element);
    myReferencedElement = referencedElement;
  }

  public SafeDeleteUsageInfo(PsiElement element, PsiElement referencedElement,
                             int startOffset, int endOffset, boolean isNonCodeUsage) {
    super(element, startOffset, endOffset, isNonCodeUsage);
    myReferencedElement = referencedElement;
  }
  public PsiElement getReferencedElement() {
    return myReferencedElement;
  }
}
