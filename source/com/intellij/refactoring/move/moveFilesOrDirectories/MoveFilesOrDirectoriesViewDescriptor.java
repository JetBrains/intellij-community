package com.intellij.refactoring.move.moveFilesOrDirectories;

import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.HelpID;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;

class MoveFilesOrDirectoriesViewDescriptor implements UsageViewDescriptor {
  private PsiElement[] myElementsToMove;
  private final boolean mySearchInComments;
  private final boolean mySearchInNonJavaFiles;
  private UsageInfo[] myUsages;
  private FindUsagesCommand myRefreshCommand;
  private String myProcessedElementsHeader;
  private String myCodeReferencesText;
  private final String myHelpID;

  public MoveFilesOrDirectoriesViewDescriptor(PsiElement[] elementsToMove, boolean isSearchInComments, boolean searchInNonJavaFiles, PsiDirectory newParent, UsageInfo[] usages, FindUsagesCommand refreshCommand) {
    myElementsToMove = elementsToMove;
    myUsages = usages;
    myRefreshCommand = refreshCommand;
    mySearchInComments = isSearchInComments;
    mySearchInNonJavaFiles = searchInNonJavaFiles;
    if (elementsToMove.length == 1) {
      myProcessedElementsHeader = UsageViewUtil.capitalize(UsageViewUtil.getType(elementsToMove[0]) + " to be moved to " + newParent.getVirtualFile().getPresentableUrl());
      myCodeReferencesText = "References in code to " + UsageViewUtil.getType(elementsToMove[0]) + " " + UsageViewUtil.getLongName(elementsToMove[0]) + " ";
    }
    else {
      if (elementsToMove[0] instanceof PsiFile) {
        myProcessedElementsHeader = UsageViewUtil.capitalize("Files to be moved to " + newParent.getVirtualFile().getPresentableUrl());
      }
      else if (elementsToMove[0] instanceof PsiDirectory){
        myProcessedElementsHeader = UsageViewUtil.capitalize("Directories to be moved to " + newParent.getVirtualFile().getPresentableUrl());
      }
      myCodeReferencesText = "References found in code ";
    }
    myHelpID = HelpID.getMoveHelpID(elementsToMove[0]);
  }

  public PsiElement[] getElements() {
    return myElementsToMove;
  }

  public UsageInfo[] getUsages() {
    return myUsages;
  }

  public void refresh(PsiElement[] elements) {
    myElementsToMove = new PsiElement[elements.length];
    for (int idx = 0; idx < elements.length; idx++) {
      myElementsToMove[idx] = elements[idx];
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
    return myRefreshCommand != null;
  }

  public boolean willUsageBeChanged(UsageInfo usageInfo) {
    return true;
  }

  public boolean canFilterMethods() {
    return true;
  }
}
