package com.intellij.refactoring.move.moveFilesOrDirectories;

import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usageView.UsageViewBundle;

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
      myProcessedElementsHeader = UsageViewUtil.capitalize(RefactoringBundle.message("move.single.element.elements.header", UsageViewUtil.getType(elementsToMove[0]), newParent.getVirtualFile().getPresentableUrl()));
      myCodeReferencesText = RefactoringBundle.message("references.in.code.to.0.1",
                                                       UsageViewUtil.getType(elementsToMove[0]), UsageViewUtil.getLongName(elementsToMove[0]));
    }
    else {
      if (elementsToMove[0] instanceof PsiFile) {
        myProcessedElementsHeader = UsageViewUtil.capitalize(RefactoringBundle.message("move.files.elements.header",
                                                                                       newParent.getVirtualFile().getPresentableUrl()));
      }
      else if (elementsToMove[0] instanceof PsiDirectory){
        myProcessedElementsHeader = UsageViewUtil.capitalize(RefactoringBundle.message("move.directories.elements.header",
                                                                                       newParent.getVirtualFile().getPresentableUrl()));
      }
      myCodeReferencesText = RefactoringBundle.message("references.found.in.code");
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
    return myCodeReferencesText + UsageViewBundle.getReferencesString(usagesCount, filesCount);
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("comments.elements.header",
                                     UsageViewBundle.getOccurencesString(usagesCount, filesCount));
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
