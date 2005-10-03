
package com.intellij.refactoring.inline;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewBundle;

class InlineViewDescriptor implements UsageViewDescriptor{
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inline.InlineViewDescriptor");

  private PsiElement myElement;
  private UsageInfo[] myUsages;
  private FindUsagesCommand myRefreshCommand;

  public InlineViewDescriptor(PsiElement element, UsageInfo[] usages, FindUsagesCommand refreshCommand) {
    myElement = element;
    myUsages = usages;
    myRefreshCommand = refreshCommand;
  }

  public PsiElement[] getElements() {
    return new PsiElement[] {myElement};
  }

  public UsageInfo[] getUsages() {
    return myUsages;
  }

  public void refresh(PsiElement[] elements) {
    if (elements.length == 1) {
      if (elements[0] instanceof PsiMethod && myElement instanceof PsiMethod) {
        myElement = elements[0];
      } else if (elements[0] instanceof PsiField && myElement instanceof PsiField) {
        myElement = elements[0];
      } else {
        LOG.assertTrue(false);
      }
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
    return myElement instanceof PsiMethod ?
           RefactoringBundle.message("inline.method.elements.header") :
           RefactoringBundle.message("inline.field.elements.header");
  }

  public boolean isSearchInText() {
    return false;
  }

  public boolean toMarkInvalidOrReadonlyUsages() {
    return true;
  }

  public String getCodeReferencesWord(){
    return INVOCATION_WORD;
  }

  public String getCommentReferencesWord(){
    return null;
  }

  public boolean cancelAvailable() {
    return true;
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("invocations.to.be.inlined", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return null;
  }

  public boolean isCancelInCommonGroup() {
    return false;
  }

  public String getHelpID() {
    return "find.refactoringPreview";
  }

  public boolean canRefresh() {
    return myRefreshCommand != null;
  }

  public boolean willUsageBeChanged(UsageInfo usageInfo) {
    return true;
  }

  public boolean canFilterMethods() {
    return true;
  }
}