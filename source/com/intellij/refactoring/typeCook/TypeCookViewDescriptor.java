package com.intellij.refactoring.typeCook;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;

class TypeCookViewDescriptor implements UsageViewDescriptor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.typeCook.TypeCookViewDescriptor");

  private PsiElement[] myElements;
  private UsageInfo[] myUsages;
  private FindUsagesCommand myRefreshCommand;

  public TypeCookViewDescriptor(PsiElement[] elements, UsageInfo[] usages, FindUsagesCommand refreshCommand) {
    myElements = elements;
    myUsages = usages;
    myRefreshCommand = refreshCommand;
  }

  public PsiElement[] getElements() {
    return myElements;
  }

  public UsageInfo[] getUsages() {
    return myUsages;
  }

  public void refresh(PsiElement[] elements) {
    if (myRefreshCommand != null) {
      myUsages = myRefreshCommand.execute(elements);
    }
  }

  public String getProcessedElementsHeader() {
    return "Scope(s) to generify";
  }

  public boolean isSearchInText() {
    return false;
  }

  public boolean toMarkInvalidOrReadonlyUsages() {
    return true;
  }

  public String getCodeReferencesWord() {
    return REFERENCE_WORD;
  }

  public String getCommentReferencesWord() {
    return null;
  }

  public boolean cancelAvailable() {
    return true;
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return "Declaration(s) to be generified " + UsageViewUtil.getUsageCountInfo(usagesCount, filesCount, "reference");
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