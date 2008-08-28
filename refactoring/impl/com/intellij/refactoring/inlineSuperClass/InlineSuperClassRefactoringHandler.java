/*
 * User: anna
 * Date: 27-Aug-2008
 */
package com.intellij.refactoring.inlineSuperClass;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import org.jetbrains.annotations.NotNull;

public class InlineSuperClassRefactoringHandler implements RefactoringActionHandler {
  public static final String REFACTORING_NAME = "Inline Super Class";
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file, final DataContext dataContext) {
    final ScrollingModel scrollingModel = editor.getScrollingModel();
    scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE);
    final PsiElement psiElement = file.findElementAt(editor.getCaretModel().getOffset());

    final PsiClass psiClass = PsiTreeUtil.getParentOfType(psiElement, PsiClass.class);
    if (psiClass == null) {
      return;
    }

    final PsiClass superClass = psiClass.getSuperClass();
    if (superClass == null) {
      return;
    }
    new InlineSuperClassRefactoringDialog(psiClass.getProject(), superClass, psiClass).show();
  }

  public void invoke(@NotNull final Project project, @NotNull final PsiElement[] elements, final DataContext dataContext) {

  }
}