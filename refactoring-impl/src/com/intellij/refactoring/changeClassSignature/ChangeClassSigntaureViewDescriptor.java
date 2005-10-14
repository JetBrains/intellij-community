package com.intellij.refactoring.changeClassSignature;

import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.openapi.diagnostic.Logger;

/**
 * @author dsl
 */
public class ChangeClassSigntaureViewDescriptor extends UsageViewDescriptorAdapter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.changeClassSignature.ChangeClassSigntaureViewDescriptor");
  private PsiClass myClass;

  public ChangeClassSigntaureViewDescriptor(PsiClass aClass, UsageInfo[] usages, FindUsagesCommand refreshCommand) {
    super(usages, refreshCommand);
    myClass = aClass;
  }

  public PsiElement[] getElements() {
    return new PsiElement[]{myClass};
  }

  public void refresh(PsiElement[] elements) {
    if (elements.length == 1 && elements[0] instanceof PsiClass) {
      myClass = (PsiClass)elements[0];
    }
    else {
      // should not happen
      LOG.assertTrue(false);
    }
    if (myRefreshCommand != null) {
      myUsages = myRefreshCommand.execute(elements);
    }
  }

  public String getProcessedElementsHeader() {
    return UsageViewUtil.capitalize(UsageViewUtil.getType(myClass));
  }
}
