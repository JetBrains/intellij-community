
package com.intellij.refactoring.encapsulateFields;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewBundle;

class EncapsulateFieldsViewDescriptor implements UsageViewDescriptor {
  private PsiField[] myFields;
  private UsageInfo[] myUsages;
  private FindUsagesCommand myRefreshCommand;

  public EncapsulateFieldsViewDescriptor(PsiField[] fields, UsageInfo[] usages, FindUsagesCommand refreshCommand) {
    myFields = fields;
    myUsages = usages;
    myRefreshCommand = refreshCommand;
  }

  public String getProcessedElementsHeader() {
    return RefactoringBundle.message("encapsulate.fields.fields.to.be.encapsulated");
  }

  public PsiElement[] getElements() {
    return myFields;
  }

  public UsageInfo[] getUsages() {
    return myUsages;
  }

  public void refresh(PsiElement[] elements) {
    if (elements.length > 1) {
      myFields = new PsiField[elements.length];
      for (int idx = 0; idx < elements.length; idx++) {
        myFields[idx] = (PsiField)elements[idx];
      }
    }
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

  public boolean cancelAvailable() {
    return true;
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("references.to.be.changed", UsageViewBundle.getReferencesString(usagesCount, filesCount));
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