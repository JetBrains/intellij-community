
package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.anonymousToInner.AnonymousToInnerHandler;

public class AnonymousToInnerAction extends BaseRefactoringAction {
  public boolean isAvailableInEditorOnly() {
    return true;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    return false;
  }

  protected boolean isAvailableOnElementInEditor(final PsiElement element) {
    if (PsiTreeUtil.getParentOfType(element, PsiAnonymousClass.class) != null) {
      return true;
    }
    final PsiNewExpression newExpression = PsiTreeUtil.getParentOfType(element, PsiNewExpression.class);
    return newExpression != null && newExpression.getAnonymousClass() != null;
  }

  public RefactoringActionHandler getHandler(DataContext dataContext) {
    return new AnonymousToInnerHandler();
  }
}