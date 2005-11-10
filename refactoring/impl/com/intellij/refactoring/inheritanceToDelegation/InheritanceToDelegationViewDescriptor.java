package com.intellij.refactoring.inheritanceToDelegation;

import com.intellij.psi.*;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;

/**
 * @author dsl
 */
public class InheritanceToDelegationViewDescriptor extends UsageViewDescriptorAdapter {
  private PsiClass myClass;

  public InheritanceToDelegationViewDescriptor(PsiClass aClass, UsageInfo[] usages) {
    super(usages);
    myClass = aClass;
  }

  public PsiElement[] getElements() {
    return new PsiElement[] { myClass };
  }

  public String getProcessedElementsHeader() {
    return RefactoringBundle.message("replace.inheritance.with.delegation.elements.header");
  }
}
