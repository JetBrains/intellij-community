package com.intellij.refactoring.replaceConstructorWithFactory;

import com.intellij.psi.*;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;

/**
 * @author dsl
 */
class ReplaceConstructorWithFactoryViewDescriptor extends UsageViewDescriptorAdapter {
  private PsiMethod myConstructor;
  private PsiClass myClass;

  public ReplaceConstructorWithFactoryViewDescriptor(UsageInfo[] usages, FindUsagesCommand refreshCommand,
                                                     PsiMethod constructor) {
    super(usages, refreshCommand);
    myConstructor = constructor;
    myClass = null;
  }

  public ReplaceConstructorWithFactoryViewDescriptor(UsageInfo[] usages, FindUsagesCommand refreshCommand, PsiClass aClass) {
    super(usages, refreshCommand);
    myClass = aClass;
    myConstructor = null;
  }

  public PsiElement[] getElements() {
    if (myConstructor != null) {
      return new PsiElement[] {myConstructor};
    } else {
      return new PsiElement[] {myClass};
    }
  }

  public void refresh(PsiElement[] elements) {
    if(elements[0] instanceof PsiMethod && ((PsiMethod) elements[0]).isConstructor()) {
      myConstructor = (PsiMethod) elements[0];
      myClass = null;
    } else if(elements[0] instanceof PsiClass) {
      myClass = (PsiClass) elements[0];
      myConstructor = null;
    }
    refreshUsages(elements);
  }

  public String getProcessedElementsHeader() {
    if (myConstructor != null) {
      return "Replace constructor with factory method";
    } else {
      return "Replace default constructor with factory method";
    }
  }
}
