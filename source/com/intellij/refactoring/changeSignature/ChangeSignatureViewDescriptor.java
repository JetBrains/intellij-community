/**
 * created at Sep 17, 2001
 * @author Jeka
 */
package com.intellij.refactoring.changeSignature;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;

class ChangeSignatureViewDescriptor implements UsageViewDescriptor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.changeSignature.ChangeSignatureViewDescriptor");

  private PsiMethod myMethod;
  private UsageInfo[] myUsages;
  private final String myProcessedElementsHeader;
  private FindUsagesCommand myRefreshCommand;

  public ChangeSignatureViewDescriptor(PsiMethod method, UsageInfo[] usages, FindUsagesCommand refreshCommand) {
    myMethod = method;
    myUsages = usages;
    myRefreshCommand = refreshCommand;
    myProcessedElementsHeader = UsageViewUtil.capitalize(UsageViewUtil.getType(method) + " to change signature");
  }

  public PsiElement[] getElements() {
    return new PsiElement[] {myMethod};
  }

  public UsageInfo[] getUsages() {
    return myUsages;
  }

  public void refresh(PsiElement[] elements) {
    if (elements.length == 1 && elements[0] instanceof PsiMethod) {
      myMethod = (PsiMethod)elements[0];
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
    return "References to be changed " + UsageViewUtil.getUsageCountInfo(usagesCount, filesCount, getCodeReferencesWord());
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
    return true;
  }

  public boolean willUsageBeChanged(UsageInfo usageInfo) {
    return true;
  }

  public boolean canFilterMethods() {
    return true;
  }
}
