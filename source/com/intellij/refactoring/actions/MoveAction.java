
package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.move.MoveHandler;

public class MoveAction extends BaseRefactoringAction {

  private MoveHandler.TargetContainerFinder myFinder = new MoveHandler.TargetContainerFinder() {
    public PsiElement getTargetContainer(DataContext dataContext) {
      return (PsiElement)dataContext.getData(DataConstantsEx.TARGET_PSI_ELEMENT);
    }
  };

  public boolean isAvailableInEditorOnly() {
    return false;
  }

  protected boolean isAvaiableForFile(PsiFile file){
    // move is supported for any file
    return true;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    return MoveHandler.canMove(elements, null);
  }

  public RefactoringActionHandler getHandler(DataContext dataContext) {
    return new MoveHandler(myFinder);
  }
}