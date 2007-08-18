package com.intellij.refactoring.invertBoolean;

import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class InvertBooleanHandler implements RefactoringActionHandler {
  static final String REFACTORING_NAME = RefactoringBundle.message("invert.boolean.title");

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = DataKeys.PSI_ELEMENT.getData(dataContext);
    if (element instanceof PsiMethod) {
      invoke(((PsiMethod)element), project);
    } else if (element instanceof PsiVariable) {
      invoke(((PsiVariable)element), project);
    } else {
      CommonRefactoringUtil.showErrorMessage(
        REFACTORING_NAME,
        RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.method.or.variable.name")),
        HelpID.INVERT_BOOLEAN,
        project);
    }
  }

  private static void invoke(PsiVariable var, final Project project) {
    final PsiType returnType = var.getType();
    if (!PsiType.BOOLEAN.equals(returnType)) {
      CommonRefactoringUtil.showErrorMessage(
        REFACTORING_NAME,
        RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("invert.boolean.wrong.type")),
        HelpID.INVERT_BOOLEAN,
        project);
      return;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, var)) return;
    if (var instanceof PsiParameter && ((PsiParameter)var).getDeclarationScope() instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)((PsiParameter)var).getDeclarationScope();
      final PsiMethod superMethod = SuperMethodWarningUtil.checkSuperMethod(method, RefactoringBundle.message("to.refactor"));
       if (superMethod != null) {
         var = superMethod.getParameterList().getParameters()[method.getParameterList().getParameterIndex((PsiParameter)var)];
       }
    }

    new InvertBooleanDialog(var).show();
  }

  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    if (elements.length == 1 && elements[0] instanceof PsiMethod) {
      invoke((PsiMethod)elements[0], project);
    }
  }

  private static void invoke(PsiMethod method, final Project project) {
    final PsiType returnType = method.getReturnType();
    if (!PsiType.BOOLEAN.equals(returnType)) {
      CommonRefactoringUtil.showErrorMessage(
        REFACTORING_NAME,
        RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("invert.boolean.wrong.type")),
        HelpID.INVERT_BOOLEAN,
        project);
      return;
    }

    final PsiMethod superMethod = SuperMethodWarningUtil.checkSuperMethod(method, RefactoringBundle.message("to.refactor"));
    if (superMethod != null) method = superMethod;

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, method)) return;

    new InvertBooleanDialog(method).show();
  }
}
