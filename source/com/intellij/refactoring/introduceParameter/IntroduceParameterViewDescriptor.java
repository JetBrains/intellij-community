/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 16.04.2002
 * Time: 15:54:37
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.introduceParameter;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;

class IntroduceParameterViewDescriptor extends UsageViewDescriptorAdapter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.makeMethodStatic.MakeMethodStaticViewDescriptor");

  private PsiMethod myMethodToSearchFor;

  public IntroduceParameterViewDescriptor(PsiMethod methodToSearchFor,
                                          UsageInfo[] usages, FindUsagesCommand refreshCommand) {
    super(usages, refreshCommand);
    myMethodToSearchFor = methodToSearchFor;

  }

  public PsiElement[] getElements() {
//    if(myMethodToReplaceIn.equals(myMethodToSearchFor)) {
//      return new PsiElement[] {myMethodToReplaceIn};
//    }
    return new PsiElement[]{myMethodToSearchFor};
  }


  public void refresh(PsiElement[] elements) {
    if(elements.length == 1 && elements[0] instanceof PsiMethod ) {
      myMethodToSearchFor = (PsiMethod) elements[0];
    }
    else {
      // should not happen
      LOG.assertTrue(false);
    }
    refreshUsages(elements);
  }

  public boolean canRefresh() {
    return false;
  }

  public String getProcessedElementsHeader() {
    return "Adding parameter to a method";
  }
}
