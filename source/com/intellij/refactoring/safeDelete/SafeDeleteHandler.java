package com.intellij.refactoring.safeDelete;

import com.intellij.ide.util.DeleteUtil;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.util.containers.HashSet;

import java.util.Arrays;
import java.util.Set;

/**
 * @author dsl
 */
public class SafeDeleteHandler implements RefactoringActionHandler {
  public static final String REFACTORING_NAME = "Safe Delete";

  public void invoke(Project project, Editor editor, PsiFile file, DataContext dataContext) {
    PsiElement element = (PsiElement) dataContext.getData(DataConstants.PSI_ELEMENT);
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    if (!SafeDeleteProcessor.validElement(element)) {
      String message =
              "Cannot perform the refactoring.\n" +
              "Caret should be positioned on the name of a class, a field, or a method to be deleted";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, /*HelpID.SAFE_DELETE*/null, project);
      return;
    }
    invoke(project, new PsiElement[]{element}, dataContext);
  }

  public void invoke(final Project project, PsiElement[] elements, DataContext dataContext) {
    invoke(project, elements, true);
  }

  public void invoke(final Project project, PsiElement[] elements, boolean checkSuperMethods) {
    for (int i = 0; i < elements.length; i++) {
      PsiElement element = elements[i];
      if (!SafeDeleteProcessor.validElement(element)) {
        return;
      }
    }
    final PsiElement[] elementsToDelete = DeleteUtil.filterElements(elements);
    Set<PsiElement> elementsSet = new HashSet<PsiElement>(Arrays.asList(elementsToDelete));

    if (checkSuperMethods) {
      for (int i = 0; i < elementsToDelete.length; i++) {
        PsiElement element = elementsToDelete[i];
        if (element instanceof PsiMethod) {
          final PsiMethod deepestSuperMethod = PsiSuperMethodUtil.findDeepestSuperMethod((PsiMethod) element);
          if (!elementsSet.contains(deepestSuperMethod)) {
            final PsiMethod method = SuperMethodWarningUtil.checkSuperMethod((PsiMethod)element,
                                                                             "delete (with usage search)");
            if (method == null) return;
            elementsToDelete[i] = method;
          }
        }
      }
    }

    for (int i = 0; i < elementsToDelete.length; i++) {
      PsiElement psiElement = elementsToDelete[i];
      if (!psiElement.isWritable()) {
        RefactoringMessageUtil.showReadOnlyElementRefactoringMessage(project, psiElement);
        return;
      }
    }

    SafeDeleteDialog dialog = new SafeDeleteDialog(project, elementsToDelete, new SafeDeleteDialog.Callback() {
      public void run(final SafeDeleteDialog dialog) {
        SafeDeleteProcessor.createInstance(project, new Runnable() {
          public void run() {
            dialog.close(SafeDeleteDialog.CANCEL_EXIT_CODE);
          }
        }, elementsToDelete, dialog.isSearchInComments(), dialog.isSearchInNonJava(), true).run(null);
      }

    });

    dialog.show();
  }
}
