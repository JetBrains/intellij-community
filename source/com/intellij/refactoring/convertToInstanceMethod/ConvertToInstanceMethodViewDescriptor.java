package com.intellij.refactoring.convertToInstanceMethod;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;

/**
 * @author dsl
 */
public class ConvertToInstanceMethodViewDescriptor extends UsageViewDescriptorAdapter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.convertToInstanceMethod.ConvertToInstanceMethodViewDescriptor");
  private PsiMethod myMethod;
  private PsiParameter myTargetParameter;
  private PsiClass myTargetClass;

  public ConvertToInstanceMethodViewDescriptor(UsageInfo[] usages,
                                               FindUsagesCommand refreshCommand,
                                               PsiMethod method,
                                               PsiParameter targetParameter,
                                               PsiClass targetClass) {
    super(usages, refreshCommand);
    myMethod = method;
    myTargetParameter = targetParameter;
    myTargetClass = targetClass;
  }

  public PsiElement[] getElements() {
    return new PsiElement[] {myMethod, myTargetParameter, myTargetClass};
  }

  public String getProcessedElementsHeader() {
    return "Convert to instance method";
  }

  public boolean canRefresh() {
    return false;
  }

  public void refresh(PsiElement[] elements) {
    if (elements.length == 3 && elements[0] instanceof PsiMethod && elements[1] instanceof PsiParameter) {
      myMethod = (PsiMethod)elements[0];
      myTargetParameter = (PsiParameter) elements[1];
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
