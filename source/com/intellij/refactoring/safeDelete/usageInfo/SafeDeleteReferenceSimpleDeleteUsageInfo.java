package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;

/**
 * @author dsl
 */
public class SafeDeleteReferenceSimpleDeleteUsageInfo extends SafeDeleteReferenceUsageInfo {

  public SafeDeleteReferenceSimpleDeleteUsageInfo(PsiElement element, PsiElement referencedElement, boolean isSafeDelete) {
    super(element, referencedElement, isSafeDelete);
  }

  public SafeDeleteReferenceSimpleDeleteUsageInfo(PsiElement element, PsiElement referencedElement,
               int startOffset, int endOffset, boolean isNonCodeUsage, boolean isSafeDelete) {
    super(element, referencedElement, startOffset, endOffset, isNonCodeUsage, isSafeDelete);
  }

  public void deleteElement() throws IncorrectOperationException {
    if(isSafeDelete()) {
      getElement().delete();
    }
  }
}
