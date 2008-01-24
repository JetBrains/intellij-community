package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.util.IncorrectOperationException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.openapi.diagnostic.Logger;

/**
 * @author yole
 */
public class SafeDeleteReferenceJavaDeleteUsageInfo extends SafeDeleteReferenceSimpleDeleteUsageInfo {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceJavaDeleteUsageInfo");

  public SafeDeleteReferenceJavaDeleteUsageInfo(PsiElement element, PsiElement referencedElement, boolean isSafeDelete) {
    super(element, referencedElement, isSafeDelete);
  }

  public SafeDeleteReferenceJavaDeleteUsageInfo(final PsiElement element, final PsiElement referencedElement, final int startOffset, final int endOffset,
                                                final boolean isNonCodeUsage,
                                                final boolean isSafeDelete) {
    super(element, referencedElement, startOffset, endOffset, isNonCodeUsage, isSafeDelete);
  }

  public void deleteElement() throws IncorrectOperationException {
    if(isSafeDelete()) {
      PsiElement element = getElement();
      LOG.assertTrue(element != null);
      PsiImportStatementBase importStatement = PsiTreeUtil.getParentOfType(element, PsiImportStatementBase.class);
      if (importStatement != null) importStatement.delete();
      else element.delete();
    }
  }
}
