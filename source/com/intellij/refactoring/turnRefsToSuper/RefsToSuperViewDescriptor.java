
package com.intellij.refactoring.turnRefsToSuper;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;

class RefsToSuperViewDescriptor implements UsageViewDescriptor{
  private final TurnRefsToSuperProcessor myProcessor;
  private PsiClass myClass;
  private PsiClass mySuper;
  private UsageInfo[] myUsages;
  private FindUsagesCommand myRefreshCommand;

  public RefsToSuperViewDescriptor(TurnRefsToSuperProcessor processor, PsiClass aClass, PsiClass anInterface, UsageInfo[] usages, FindUsagesCommand refreshCommand) {
    myProcessor = processor;
    myClass = aClass;
    mySuper = anInterface;
    myUsages = usages;
    myRefreshCommand = refreshCommand;
  }

  public PsiElement[] getElements() {
    return new PsiElement[] {myClass, mySuper};
  }

  public UsageInfo[] getUsages() {
    return myUsages;
  }

  public void refresh(PsiElement[] elements) {
    myClass = (PsiClass)elements[0];
    mySuper = (PsiClass)elements[1];
    myProcessor.setClasses(myClass, mySuper);
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
    StringBuffer buffer = new StringBuffer();
    buffer.append("References to ");
    buffer.append(myClass.isInterface() ? "interface " : "class ");
    buffer.append(myClass.getName());
    buffer.append(" to be replaced with references to ");
    buffer.append(mySuper.isInterface() ? "interface " : "class ");
    buffer.append(mySuper.getName());
    buffer.append(" ");
    buffer.append(UsageViewUtil.getUsageCountInfo(usagesCount, filesCount, "reference"));
    return buffer.toString();
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