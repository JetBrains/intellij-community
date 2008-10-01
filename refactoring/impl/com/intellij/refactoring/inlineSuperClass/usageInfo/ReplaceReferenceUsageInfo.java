/*
 * User: anna
 * Date: 27-Aug-2008
 */
package com.intellij.refactoring.inlineSuperClass.usageInfo;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class ReplaceReferenceUsageInfo extends FixableUsageInfo {
  public static final Logger LOG = Logger.getInstance("#" + ReplaceReferenceUsageInfo.class.getName());
  private final PsiExpression myReferenceExpression;
  private final PsiClass myTargetClass;

  public ReplaceReferenceUsageInfo(PsiExpression referenceExpression, PsiClass targetClass) {
    super(referenceExpression);
    myReferenceExpression = referenceExpression;
    myTargetClass = targetClass;
  }

  public void fixUsage() throws IncorrectOperationException {
    if (myReferenceExpression.isValid()) {
      final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myReferenceExpression.getProject()).getElementFactory();
      myReferenceExpression.replace(elementFactory.createReferenceExpression(myTargetClass));
    }
  }


}