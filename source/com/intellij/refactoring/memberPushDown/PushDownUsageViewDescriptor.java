package com.intellij.refactoring.memberPushDown;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;

class PushDownUsageViewDescriptor implements UsageViewDescriptor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.memberPushDown.PushDownUsageViewDescriptor");
  private PsiClass myClass;
  private UsageInfo[] myUsages;
  private FindUsagesCommand myRefreshCommand;
  private final String myProcessedElementsHeader = "Push down members from";

  public PushDownUsageViewDescriptor(PsiClass aClass, UsageInfo[] usages, FindUsagesCommand refreshComamnd) {
    myClass = aClass;
    myUsages = usages;
    myRefreshCommand = refreshComamnd;
  }

  public UsageInfo[] getUsages() {
    return myUsages;
  }

  public void refresh(PsiElement[] elements) {
    if (elements.length == 1 && elements[0] instanceof PsiClass) {
      myClass = (PsiClass) elements[0];
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
    return myProcessedElementsHeader;
  }

  public PsiElement[] getElements() {
    return new PsiElement[]{myClass};
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
    return "Classes to push down members to " + UsageViewUtil.getUsageCountInfo(usagesCount, filesCount, REFERENCE_WORD);
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return null;
  }

  public boolean isCancelInCommonGroup() {
    return false;
  }

  public boolean canRefresh() {
    return false;
  }

  public boolean willUsageBeChanged(UsageInfo usageInfo) {
    return true;
  }

  public String getHelpID() {
    return null/*HelpID.MEMBERS_PUSH_DOWN*/;
  }

  public boolean canFilterMethods() {
    return true;
  }
}
