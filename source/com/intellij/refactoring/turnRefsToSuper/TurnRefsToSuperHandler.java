/**
 * created at Oct 25, 2001
 * @author Jeka
 */
package com.intellij.refactoring.turnRefsToSuper;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.refactoring.util.RefactoringMessageUtil;

import java.util.ArrayList;

public class TurnRefsToSuperHandler implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.turnRefsToSuper.TurnRefsToSuperHandler");

  public static final String REFACTORING_NAME = "Use Interface Where Possible";

  private Project myProject;


  public void invoke(Project project, Editor editor, PsiFile file, DataContext dataContext) {
    int offset = editor.getCaretModel().getOffset();
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = file.findElementAt(offset);
    while (true) {
      if (element == null || element instanceof PsiFile) {
        String message =
                "Cannot perform the refactoring.\n" +
                "The caret should be positioned inside the class to be refactored.";
        RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.EXTRACT_SUPERCLASS, project);
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

    myProject = project;
    PsiClass subClass = (PsiClass) elements[0];

    ArrayList basesList = RefactoringHierarchyUtil.createBasesList(subClass, true, true);

    if (basesList.isEmpty()) {
      String message =
              "Cannot perform the refactoring.\n" +
              "Interface " + subClass.getQualifiedName() + " does not have base interfaces.";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, null/*HelpID.TURN_REFS_TO_SUPER*/, project);
      return;
    }

    TurnRefsToSuperDialog dialog = new TurnRefsToSuperDialog(myProject, subClass, basesList);
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }

    new TurnRefsToSuperProcessor(
            myProject, subClass, dialog.getSuperClass(), dialog.isUseInInstanceOf(),
            dialog.isPreviewUsages()
    ).run(null);
  }

}
