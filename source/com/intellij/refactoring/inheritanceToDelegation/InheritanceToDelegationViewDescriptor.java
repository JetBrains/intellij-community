package com.intellij.refactoring.inheritanceToDelegation;

import com.intellij.psi.*;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;

/**
 * @author dsl
 */
public class InheritanceToDelegationViewDescriptor extends UsageViewDescriptorAdapter {
  private PsiClass myClass;

  public InheritanceToDelegationViewDescriptor(PsiClass aClass, UsageInfo[] usages, FindUsagesCommand refreshCommand) {
    super(usages, refreshCommand);
    myClass = aClass;
  }

  public boolean canRefresh() {
    return false;
  }

  public PsiElement[] getElements() {
    return new PsiElement[] { myClass };
  }

  public void refresh(PsiElement[] elements) {
  }

  public String getProcessedElementsHeader() {
    return "Replace inheritance with delegation";
  }
}
