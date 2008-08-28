/*
 * User: anna
 * Date: 27-Aug-2008
 */
package com.intellij.refactoring.inlineSuperClass.usageInfo;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class ReplaceWithSubtypeUsageInfo extends FixableUsageInfo{
  public static final Logger LOG = Logger.getInstance("#" + ReplaceWithSubtypeUsageInfo.class.getName());
  private final PsiTypeElement myTypeElement;
  private final PsiClassType myTargetClassType;
  private PsiType myOriginalType;

  public ReplaceWithSubtypeUsageInfo(PsiTypeElement typeElement, PsiClassType classType) {
    super(typeElement);
    myTypeElement = typeElement;
    myTargetClassType = classType;
    myOriginalType = myTypeElement.getType();
  }

  public void fixUsage() throws IncorrectOperationException {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myTypeElement.getProject()).getElementFactory();
    myTypeElement.replace(elementFactory.createTypeElement(myTargetClassType));
  }

  public PsiClassType getTargetClassType() {
    return myTargetClassType;
  }

  public PsiType getOriginalType() {
    return myOriginalType;
  }
}