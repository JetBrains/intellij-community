package com.intellij.refactoring.ui;

import com.intellij.psi.PsiElement;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.refactoring.RefactoringBundle;

/**
 * @author dsl
 */
public abstract class UsageViewDescriptorAdapter implements UsageViewDescriptor {
  protected UsageInfo[] myUsages;

  public UsageViewDescriptorAdapter(UsageInfo[] usages, FindUsagesCommand refreshCommand) {
    myUsages = usages;
    myRefreshCommand = refreshCommand;
  }


  protected FindUsagesCommand myRefreshCommand;

  public UsageInfo[] getUsages() {
    return myUsages;
  }

  protected void refreshUsages(PsiElement[] elements) {
    if (myRefreshCommand != null) {
      myUsages = myRefreshCommand.execute(elements);
    }
  }


  public boolean isSearchInText() {
    return false;
  }

  public boolean toMarkInvalidOrReadonlyUsages() {
    return true;
  }

  public String getCodeReferencesWord(){
    return REFERENCE_WORD;
  }

  public String getCommentReferencesWord(){
    return null;
  }

  public boolean isCancelInCommonGroup() {
    return false;
  }

  public boolean cancelAvailable() {
    return true;
  }

  public boolean canRefresh() {
    return myRefreshCommand != null;
  }

  public boolean willUsageBeChanged(UsageInfo usageInfo) {
    return true;
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("references.to.be.changed", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return null;
  }

  public String getHelpID() {
    return "find.refactoringPreview";
  }

  public boolean canFilterMethods() {
    return true;
  }
}
