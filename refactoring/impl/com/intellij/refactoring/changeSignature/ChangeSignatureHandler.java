package com.intellij.refactoring.changeSignature;

import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeClassSignature.ChangeClassSignatureDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;

public class ChangeSignatureHandler implements RefactoringActionHandler {
  public static final String REFACTORING_NAME = RefactoringBundle.message("changeSignature.refactoring.name");

  public void invoke(Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = (PsiElement) dataContext.getData(DataConstants.PSI_ELEMENT);
    if (element instanceof PsiMethod) {
      invoke((PsiMethod) element, project);
    }
    else if (element instanceof PsiClass) {
      invoke((PsiClass) element);
    }
    else {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.method.or.class.name"));
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.CHANGE_SIGNATURE, project);
    }
  }

  public void invoke(final Project project, final PsiElement[] elements, final DataContext dataContext) {
    if (elements.length != 1) return;
    if (elements[0] instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)elements[0];

      invoke(method, project);
    }
    else if (elements[0] instanceof PsiClass){
      invoke((PsiClass) elements[0]);
    }
  }

  private void invoke(final PsiMethod method, final Project project) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, method)) return;

    final String actionString = RefactoringBundle.message("to.refactor");
    PsiMethod newMethod = SuperMethodWarningUtil.checkSuperMethod(method, actionString);
    if (newMethod == null) return;

    if (!newMethod.equals(method)) {
      invoke(newMethod, project);
      return;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, method)) return;

    final PsiClass containingClass = method.getContainingClass();
    final ChangeSignatureDialog dialog = new ChangeSignatureDialog(project, method, containingClass != null && !containingClass.isInterface());
    dialog.show();
  }

  private void invoke(final PsiClass aClass) {
    final PsiTypeParameterList typeParameterList = aClass.getTypeParameterList();
    Project project = aClass.getProject();
    if (typeParameterList == null) {
      final String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("changeClassSignature.no.type.parameters"));
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.CHANGE_CLASS_SIGNATURE, project);
    }
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, aClass)) return;

    ChangeClassSignatureDialog dialog = new ChangeClassSignatureDialog(aClass);
    dialog.show();
  }
}