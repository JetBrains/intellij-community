
package com.intellij.refactoring.actions;

import com.intellij.j2ee.module.view.J2EEProjectViewPane;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import com.intellij.refactoring.safeDelete.SafeDeleteProcessor;
import com.intellij.lang.Language;
import com.intellij.javaee.model.common.CmrField;
import com.intellij.javaee.model.common.CmpField;

public class SafeDeleteAction extends BaseRefactoringAction {
  public boolean isAvailableInEditorOnly() {
    return false;
  }

  protected boolean isAvailableForLanguage(Language language) {
    return true;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    for (PsiElement element : elements) {
      if (!SafeDeleteProcessor.validElement(element)) return false;
    }
    return true;
  }

  public RefactoringActionHandler getHandler(DataContext dataContext) {
    return new SafeDeleteHandler();
  }

  protected boolean isEnabledOnDataContext(DataContext dataContext) {
    final Object ejbElement = dataContext.getData(J2EEProjectViewPane.SELECTED_ELEMENT);
    // CMP/CMR fields should be deleted from EjbImpl View
    if (ejbElement instanceof CmpField || ejbElement instanceof CmrField) return false;
    return super.isEnabledOnDataContext(dataContext);
  }
}