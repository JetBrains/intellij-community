/**
 * created at Sep 17, 2001
 * @author Jeka
 */
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;

class MoveClassesOrPackagesViewDescriptor implements UsageViewDescriptor {
  private PsiElement[] myPsiElements;
  private final boolean mySearchInComments;
  private final boolean mySearchInNonJavaFiles;
  private PackageWrapper myTargetPackage;
  private String myNewParentPackageName;
  private UsageInfo[] myUsages;
  private FindUsagesCommand myRefreshCommand;
  private String myProcessedElementsHeader;
  private String myCodeReferencesText;
  private final String myHelpID;

  public MoveClassesOrPackagesViewDescriptor(PsiElement[] psiElements,
                                             boolean isSearchInComments,
                                             boolean searchInNonJavaFiles, PackageWrapper newParent,
                                             UsageInfo[] usages, FindUsagesCommand refreshCommand) {
    myPsiElements = psiElements;
    myUsages = usages;
    myRefreshCommand = refreshCommand;
    mySearchInComments = isSearchInComments;
    mySearchInNonJavaFiles = searchInNonJavaFiles;
    myTargetPackage = newParent;
    myNewParentPackageName = MoveClassesOrPackagesUtil.getPackageName(myTargetPackage);
    if (psiElements.length == 1) {
      myProcessedElementsHeader = UsageViewUtil.capitalize(UsageViewUtil.getType(psiElements[0]) + " to be moved to " + myNewParentPackageName);
      myCodeReferencesText = "References in code to " + UsageViewUtil.getType(psiElements[0]) + " " + UsageViewUtil.getLongName(psiElements[0]) + " ";
    }
    else {
      if (psiElements[0] instanceof PsiClass) {
        myProcessedElementsHeader = UsageViewUtil.capitalize("Classes to be moved to " + myNewParentPackageName);
      }
      else if (psiElements[0] instanceof PsiDirectory){
        myProcessedElementsHeader = UsageViewUtil.capitalize("Packages to be moved to " + myNewParentPackageName);
      }
      myCodeReferencesText = "References found in code ";
    }
    myHelpID = HelpID.getMoveHelpID(psiElements[0]);
  }

  public PsiElement[] getElements() {
    return myPsiElements;
  }

  public UsageInfo[] getUsages() {
    return myUsages;
  }

  public void refresh(PsiElement[] elements) {
    myPsiElements = new PsiElement[elements.length];
    for (int idx = 0; idx < elements.length; idx++) {
      myPsiElements[idx] = elements[idx];
    }
    if (myRefreshCommand != null) {
      myUsages = myRefreshCommand.execute(elements);
    }
  }

  public String getProcessedElementsHeader() {
    return myProcessedElementsHeader;
  }

  public boolean isSearchInText() {
    return mySearchInComments || mySearchInNonJavaFiles;
  }

  public boolean toMarkInvalidOrReadonlyUsages() {
    return true;
  }

  public String getCodeReferencesWord(){
    return REFERENCE_WORD;
  }

  public String getCommentReferencesWord(){
    return OCCURRENCE_WORD;
  }

  public boolean cancelAvailable() {
    return true;
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return myCodeReferencesText + UsageViewUtil.getUsageCountInfo(usagesCount, filesCount, "reference");
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return "Occurrences found in comments, strings and non-java files " + UsageViewUtil.getUsageCountInfo(usagesCount, filesCount, "occurrence");
  }

  public boolean isCancelInCommonGroup() {
    return false;
  }

  public String getHelpID() {
    return myHelpID;
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
