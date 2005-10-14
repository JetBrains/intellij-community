package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

/**
 * @author dsl
 */
public abstract class SafeDeleteReferenceUsageInfo extends SafeDeleteUsageInfo {
  protected final boolean mySafeDelete;

  public boolean isSafeDelete() {
    return !isNonCodeUsage && mySafeDelete;
  }

  public abstract void deleteElement() throws IncorrectOperationException;

  public SafeDeleteReferenceUsageInfo(PsiElement element, PsiElement referencedElement,
                                      int startOffset, int endOffset, boolean isNonCodeUsage, boolean isSafeDelete) {
    super(element, referencedElement, startOffset, endOffset, isNonCodeUsage);
    mySafeDelete = isSafeDelete;
  }

  public SafeDeleteReferenceUsageInfo(PsiElement element, PsiElement referencedElement, boolean safeDelete) {
    super(element, referencedElement);
    mySafeDelete = safeDelete;
  }
}
