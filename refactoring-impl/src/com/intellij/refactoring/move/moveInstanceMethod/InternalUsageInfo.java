package com.intellij.refactoring.move.moveInstanceMethod;

import com.intellij.usageView.UsageInfo;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.openapi.diagnostic.Logger;

/**
 * @author ven
 */
public class InternalUsageInfo extends UsageInfo {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.moveInstanceMethod.InternalUsageInfo");
  public InternalUsageInfo(final PsiElement referenceElement) {
    super(referenceElement);
    LOG.assertTrue(referenceElement instanceof PsiReferenceExpression || referenceElement instanceof PsiNewExpression);
  }

  PsiExpression getQualifier () {
    PsiElement element = getElement();
    if (element instanceof PsiReferenceExpression) {
      return ((PsiReferenceExpression)element).getQualifierExpression();
    }
    else if (element instanceof PsiNewExpression) return ((PsiNewExpression)element).getQualifier();

    return null;
  }
}
