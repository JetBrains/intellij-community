package com.intellij.refactoring.actions;

import com.intellij.ide.DataManager;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.typeCook.TypeCookHandler;

public class TypeCookAction extends BaseRefactoringAction {

  protected boolean isAvailableInEditorOnly() {
    return false; 
  }

  public boolean isAvailableForLanguage(Language language) {
    return language.equals(StdFileTypes.JAVA.getLanguage());
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    Project project = DataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());

    if (project == null) {
      return false;
    }

    for (int i = 0; i < elements.length; i++) {
      PsiElement element = elements[i];

      if (
        !(element instanceof PsiClass ||
          element instanceof PsiJavaFile ||
          element instanceof PsiDirectory ||
          element instanceof PsiPackage
         )
      ) {
        return false;
      }
    }

    return true;
  }

  public RefactoringActionHandler getHandler(DataContext dataContext) {
    return getHandler();
  }
  public RefactoringActionHandler getHandler() {
    return new TypeCookHandler();
  }
}
