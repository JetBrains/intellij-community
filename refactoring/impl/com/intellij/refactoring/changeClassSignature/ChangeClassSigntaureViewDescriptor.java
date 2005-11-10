package com.intellij.refactoring.changeClassSignature;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewUtil;

/**
 * @author dsl
 */
public class ChangeClassSigntaureViewDescriptor extends UsageViewDescriptorAdapter {
  private PsiClass myClass;

  public ChangeClassSigntaureViewDescriptor(PsiClass aClass, UsageInfo[] usages) {
    super(usages);
    myClass = aClass;
  }

  public PsiElement[] getElements() {
    return new PsiElement[]{myClass};
  }

  public String getProcessedElementsHeader() {
    return UsageViewUtil.capitalize(UsageViewUtil.getType(myClass));
  }
}
