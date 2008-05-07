/*
 * User: anna
 * Date: 06-May-2008
 */
package com.intellij.refactoring.replaceMethodWithMethodObject;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;

public class ReplaceMethodWithMethodObjectHandler implements RefactoringActionHandler{
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file, final DataContext dataContext) {
    final int offset = editor.getCaretModel().getOffset();
    final PsiElement element = file.findElementAt(offset);
    final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    if (method == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("locate.caret.inside.a.method"));
      showErrorMessage(message, project);
      return;
    }
    if (method.isConstructor()) {
      String message = RefactoringBundle.getCannotRefactorMessage("Replace method with method object doesn't work on constructors");
      showErrorMessage(message, project);
      return;
    }
    final PsiCodeBlock body = method.getBody();
    if (body == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("method.does.not.have.a.body", method.getName()));
      showErrorMessage(message, project);
      return;
    }

    

    new ReplaceMethodWithMethodObjectDialog(method).show();
  }

  public void invoke(@NotNull final Project project, @NotNull final PsiElement[] elements, final DataContext dataContext) {
     throw new UnsupportedOperationException();
  }

  private static void showErrorMessage(String message, Project project) {
    CommonRefactoringUtil.showErrorMessage(ReplaceMethodWithMethodObjectProcessor.REFACTORING_NAME, message, HelpID.METHOD_DUPLICATES, project);
  }
}