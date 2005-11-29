package com.intellij.refactoring.typeCook;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;

public class TypeCookHandler implements RefactoringActionHandler {

  public void invoke(Project project, Editor editor, PsiFile file, DataContext dataContext) {
    invoke(project, new PsiElement[]{file}, dataContext);
  }

  public void invoke(Project project, PsiElement[] elements, DataContext dataContext) {
    if (elements == null || elements.length == 0) return;

    for (PsiElement element : elements) {
      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, element)) return;
    }

    new TypeCookDialog(project, elements).show();
  }

}
