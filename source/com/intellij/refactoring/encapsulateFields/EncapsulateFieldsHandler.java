package com.intellij.refactoring.encapsulateFields;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.util.RefactoringMessageUtil;

import java.util.HashSet;

public class EncapsulateFieldsHandler implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.encapsulateFields.EncapsulateFieldsHandler");
  public static final String REFACTORING_NAME = "Encapsulate Fields";

  public void invoke(Project project, Editor editor, PsiFile file, DataContext dataContext) {
    int offset = editor.getCaretModel().getOffset();
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = file.findElementAt(offset);
    while (true) {
      if (element == null || element instanceof PsiFile) {
        String message = "Cannot perform the refactoring.\n" +
                "The caret should be positioned inside the class to be refactored.";
        RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.ENCAPSULATE_FIELDS, project);
        return;
      }
      if (element instanceof PsiField) {
        if (((PsiField) element).getContainingClass() == null) {
          String message = "Cannot perform the refactoring.\n" +
                  "The field should be declared in a class.";
          RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.ENCAPSULATE_FIELDS, project);
          return;
        }
        invoke(project, new PsiElement[]{element}, dataContext);
        return;
      }
      if (element instanceof PsiClass) {
        invoke(project, new PsiElement[]{element}, dataContext);
        return;
      }
      element = element.getParent();
    }
  }

  /**
   * if elements.length == 1 the expected value is either PsiClass or PsiField
   * if elements.length > 1 the expected values are PsiField objects only
   */
  public void invoke(final Project project, final PsiElement[] elements, DataContext dataContext) {
    PsiClass aClass = null;
    final HashSet preselectedFields = new HashSet();
    if (elements.length == 1) {
      if (elements[0] instanceof PsiClass) {
        aClass = (PsiClass) elements[0];
      } else if (elements[0] instanceof PsiField) {
        PsiField field = (PsiField) elements[0];
        aClass = field.getContainingClass();
        preselectedFields.add(field);
      } else {
        return;
      }
    } else {
      for (int idx = 0; idx < elements.length; idx++) {
        PsiElement element = elements[idx];
        if (!(element instanceof PsiField)) {
          return;
        }
        PsiField field = (PsiField) element;
        if (aClass == null) {
          aClass = field.getContainingClass();
          preselectedFields.add(field);
        } else {
          if (aClass.equals(field.getContainingClass())) {
            preselectedFields.add(field);
          } else {
            String message = "Cannot perform the refactoring.\nFields to be refactored should belong to the same class.";
            RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.ENCAPSULATE_FIELDS, project);
            return;
          }
        }
      }
    }

    LOG.assertTrue(aClass != null);

    if (aClass.isInterface()) {
      String message = REFACTORING_NAME + " refactoring cannot be applied to interface";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.ENCAPSULATE_FIELDS, project);
      return;
    }

    if (!aClass.isWritable()) {
      RefactoringMessageUtil.showReadOnlyElementRefactoringMessage(project, aClass);
      return;
    }

    EncapsulateFieldsDialog dialog = new EncapsulateFieldsDialog(
            project,
            aClass,
            preselectedFields,
            new EncapsulateFieldsProcessor(project)
    );
    dialog.show();
  }
}