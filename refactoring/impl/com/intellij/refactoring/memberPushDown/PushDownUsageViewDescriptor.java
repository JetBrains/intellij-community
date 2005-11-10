package com.intellij.refactoring.memberPushDown;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;

class PushDownUsageViewDescriptor implements UsageViewDescriptor {
  private PsiClass myClass;
  private UsageInfo[] myUsages;
  private final String myProcessedElementsHeader = RefactoringBundle.message("push.down.members.elements.header");

  public PushDownUsageViewDescriptor(PsiClass aClass, UsageInfo[] usages) {
    myClass = aClass;
    myUsages = usages;
  }

  public UsageInfo[] getUsages() {
    return myUsages;
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
    return RefactoringBundle.message("classes.to.push.down.members.to", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return null;
  }

  public boolean isCancelInCommonGroup() {
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
