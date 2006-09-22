/**
 * created at Oct 25, 2001
 * @author Jeka
 */
package com.intellij.refactoring.turnRefsToSuper;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;

import java.util.ArrayList;

public class TurnRefsToSuperHandler implements RefactoringActionHandler {
  public static final String REFACTORING_NAME = RefactoringBundle.message("use.interface.where.possible.title");


  public void invoke(Project project, Editor editor, PsiFile file, DataContext dataContext) {
    int offset = editor.getCaretModel().getOffset();
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = file.findElementAt(offset);
    while (true) {
      if (element == null || element instanceof PsiFile) {
        String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.class"));
        CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.TURN_REFS_TO_SUPER, project);
        return;
      }
      if (element instanceof PsiClass && !(element instanceof PsiAnonymousClass)) {
        invoke(project, new PsiElement[]{element}, dataContext);
        return;
      }
      element = element.getParent();
    }
  }

  public void invoke(final Project project, PsiElement[] elements, DataContext dataContext) {
    if (elements.length != 1) return;

        PsiClass subClass = (PsiClass) elements[0];

    ArrayList basesList = RefactoringHierarchyUtil.createBasesList(subClass, true, true);

    if (basesList.isEmpty()) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("interface.does.not.have.base.interfaces", subClass.getQualifiedName()));
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, null/*HelpID.TURN_REFS_TO_SUPER*/, project);
      return;
    }

    new TurnRefsToSuperDialog(project, subClass, basesList).show();
  }

}
