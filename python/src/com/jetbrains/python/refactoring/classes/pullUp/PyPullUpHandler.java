package com.jetbrains.python.refactoring.classes.pullUp;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandler;
import org.jetbrains.annotations.NotNull;

/**
 * @author: Dennis.Ushakov
 */
public class PyPullUpHandler implements RefactoringActionHandler {
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {

  }

  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    
  }
}
