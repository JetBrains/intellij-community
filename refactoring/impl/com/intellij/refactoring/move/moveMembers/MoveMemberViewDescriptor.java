/**
 * created at Sep 11, 2001
 * @author Jeka
 */
package com.intellij.refactoring.move.moveMembers;

import com.intellij.psi.PsiElement;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.refactoring.RefactoringBundle;

class MoveMemberViewDescriptor implements UsageViewDescriptor {
  private PsiElement[] myElementsToMove;
  private UsageInfo[] myUsages;
  private FindUsagesCommand myRefreshCommand;

  public MoveMemberViewDescriptor(PsiElement[] elementsToMove, UsageInfo[] usages, FindUsagesCommand refreshCommand) {
    myElementsToMove = elementsToMove;
    myUsages = usages;
    myRefreshCommand = refreshCommand;
  }

  public PsiElement[] getElements() {
    return myElementsToMove;
  }

  public UsageInfo[] getUsages() {
    return myUsages;
  }

  public void refresh(PsiElement[] elements) {
    for (int idx = 0; idx < elements.length; idx++) {
      myElementsToMove[idx] = elements[idx];
    }
    if (myRefreshCommand != null) {
      myUsages = myRefreshCommand.execute(elements);
    }
  }

  public String getProcessedElementsHeader() {
    return RefactoringBundle.message("move.members.elements.header");
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
