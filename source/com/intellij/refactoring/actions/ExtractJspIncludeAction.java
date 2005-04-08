
package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.jsp.JspFile;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.jsp.extractInclude.ExtractIncludeFileHandler;

/**
 *
 */
public class ExtractJspIncludeAction extends BaseRefactoringAction {
  public boolean isAvailableInEditorOnly() {
    return true;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    return false;
  }

  protected boolean isAvaiableForFile(PsiFile file) {
    return file instanceof JspFile;
  }

  public RefactoringActionHandler getHandler(DataContext dataContext) {
    return new ExtractIncludeFileHandler();
  }
}
