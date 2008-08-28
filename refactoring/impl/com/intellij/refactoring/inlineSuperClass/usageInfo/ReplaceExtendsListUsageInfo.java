/*
 * User: anna
 * Date: 27-Aug-2008
 */
package com.intellij.refactoring.inlineSuperClass.usageInfo;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteExtendsClassUsageInfo;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class ReplaceExtendsListUsageInfo extends FixableUsageInfo {
  private SafeDeleteExtendsClassUsageInfo mySafeDeleteUsageInfo;

  public ReplaceExtendsListUsageInfo(PsiJavaCodeReferenceElement element, final PsiClass superClass, final PsiClass targetClass) {
    super(element);
    mySafeDeleteUsageInfo = new SafeDeleteExtendsClassUsageInfo(element, superClass, targetClass);
  }

  public void fixUsage() throws IncorrectOperationException {
    if (mySafeDeleteUsageInfo.isSafeDelete()) {
      mySafeDeleteUsageInfo.deleteElement();
    }
  }
}