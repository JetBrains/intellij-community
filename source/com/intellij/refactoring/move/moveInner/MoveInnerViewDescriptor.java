/**
 * created at Sep 11, 2001
 * @author Jeka
 */
package com.intellij.refactoring.move.moveInner;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;

class MoveInnerViewDescriptor implements UsageViewDescriptor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.moveInner.MoveInnerViewDescriptor");

  private PsiClass myInnerClass;
  private UsageInfo[] myUsages;
  private FindUsagesCommand myRefreshCommand;

  public MoveInnerViewDescriptor(PsiClass innerClass, UsageInfo[] usages, FindUsagesCommand refreshCommand) {
    myInnerClass = innerClass;
    myUsages = usages;
    myRefreshCommand = refreshCommand;
  }

  public PsiElement[] getElements() {
    return new PsiElement[] {myInnerClass};
  }

  public UsageInfo[] getUsages() {
    return myUsages;
  }

  public void refresh(PsiElement[] elements) {
    // TODO
    if (elements.length == 1 && elements[0] instanceof PsiClass) {
      myInnerClass = (PsiClass)elements[0];
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
    return null;
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
    return "References to change " + UsageViewUtil.getUsageCountInfo(usagesCount, filesCount, "reference");
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
