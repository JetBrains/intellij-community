package com.intellij.refactoring.actions;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.typeCook.TypeCookHandler;

public class TypeCookAction extends BaseRefactoringAction {

  protected boolean isAvailableInEditorOnly() {
    return false; 
  }

  public boolean isAvailableInEditorOnlyTyp() {
    return false;
  }

  public boolean isAvaiableForFile(PsiFile file) {
    return isEnabledOnElements(new PsiElement[]{file});
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    Project project = (Project)DataManager.getInstance().getDataContext().getData(DataConstants.PROJECT);

    if (project == null) {
      return false;
    }
    LanguageLevel languageLevel = PsiManager.getInstance(project).getEffectiveLanguageLevel();
    if (languageLevel.compareTo(LanguageLevel.JDK_1_5) < 0) return false;

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
