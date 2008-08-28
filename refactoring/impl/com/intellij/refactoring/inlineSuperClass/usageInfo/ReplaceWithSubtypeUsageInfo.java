/*
 * User: anna
 * Date: 27-Aug-2008
 */
package com.intellij.refactoring.inlineSuperClass.usageInfo;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiTypeElement;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class ReplaceWithSubtypeUsageInfo extends FixableUsageInfo{
  public static final Logger LOG = Logger.getInstance("#" + ReplaceWithSubtypeUsageInfo.class.getName());
  private final PsiTypeElement myTypeElement;
  private final PsiClass myTargetClass;

  public ReplaceWithSubtypeUsageInfo(PsiTypeElement typeElement, PsiClass targetClass) {
    super(typeElement);
    myTypeElement = typeElement;
    myTargetClass = targetClass;
  }

  public void fixUsage() throws IncorrectOperationException {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myTypeElement.getProject()).getElementFactory();
    myTypeElement.replace(elementFactory.createTypeElement(elementFactory.createType(myTargetClass)));
  }
}