package com.intellij.refactoring.move.moveInstanceMethod;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;

/**
 * @author dsl
 */
public class MoveInstanceMethodViewDescriptor extends UsageViewDescriptorAdapter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodViewDescriptor");
  private PsiMethod myMethod;
  private PsiVariable myTargetVariable;
  private PsiClass myTargetClass;

  public MoveInstanceMethodViewDescriptor(UsageInfo[] usages,
                                               FindUsagesCommand refreshCommand,
                                               PsiMethod method,
                                               PsiVariable targetVariable,
                                               PsiClass targetClass) {
    super(usages, refreshCommand);
    myMethod = method;
    myTargetVariable = targetVariable;
    myTargetClass = targetClass;
  }

  public PsiElement[] getElements() {
    return new PsiElement[] {myMethod, myTargetVariable, myTargetClass};
  }

  public String getProcessedElementsHeader() {
    return "Move instance method";
  }

  public boolean canRefresh() {
    return false;
  }

  public void refresh(PsiElement[] elements) {
    if (elements.length == 3 && elements[0] instanceof PsiMethod && elements[1] instanceof PsiVariable && elements[2] instanceof PsiClass) {
      myMethod = (PsiMethod)elements[0];
      myTargetVariable = (PsiVariable) elements[1];
      myTargetClass = (PsiClass) elements[2];
    }
    else {
      // should not happen
      LOG.assertTrue(false);
    }
    if (myRefreshCommand != null) {
      myUsages = myRefreshCommand.execute(elements);
    }
  }

}
