
package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.move.MoveHandler;
import com.intellij.lang.Language;

public class MoveAction extends BaseRefactoringAction {
  /**
   * @fabrique
   */
  public static final String MOVE_PROVIDER = "MoveProvider";

  private MoveHandler.TargetContainerFinder myFinder = new MoveHandler.TargetContainerFinder() {
    public PsiElement getTargetContainer(DataContext dataContext) {
      return (PsiElement)dataContext.getData(DataConstantsEx.TARGET_PSI_ELEMENT);
    }
  };
  private MoveProvider myDefaultMoveProvider = new MoveProvider() {
    public boolean isEnabledOnDataContext(DataContext dataContext) {
      return false;
    }

    public RefactoringActionHandler getHandler(DataContext dataContext) {
      return new MoveHandler(myFinder);
    }
  };

  public boolean isAvailableInEditorOnly() {
    return false;
  }

  protected boolean isAvailableForLanguage(Language language){
    // move is supported in any language
    return true;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    return MoveHandler.canMove(elements, null);
  }

  protected boolean isEnabledOnDataContext(DataContext dataContext) {
    return getMoveProvider(dataContext).isEnabledOnDataContext(dataContext);
  }

  private MoveProvider getMoveProvider(DataContext dataContext) {
    final MoveProvider moveProvider = (MoveProvider)dataContext.getData(MOVE_PROVIDER);
    if (moveProvider != null) {
      return moveProvider;
    }

    return myDefaultMoveProvider;
  }

  public RefactoringActionHandler getHandler(DataContext dataContext) {
    return getMoveProvider(dataContext).getHandler(dataContext);
  }

  public interface MoveProvider {
    boolean isEnabledOnDataContext(DataContext dataContext);
    RefactoringActionHandler getHandler(DataContext dataContext);
  }
}