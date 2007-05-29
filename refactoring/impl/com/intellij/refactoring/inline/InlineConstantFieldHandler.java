package com.intellij.refactoring.inline;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;

/**
 * @author ven
 */
public class InlineConstantFieldHandler {
  private static final String REFACTORING_NAME = RefactoringBundle.message("inline.field.title");

  private InlineConstantFieldHandler() {
  }

  public static void invoke(Project project, Editor editor, PsiField field) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, field)) return;

    if (!field.hasModifierProperty(PsiModifier.FINAL)) {
      String message = RefactoringBundle.message("0.refactoring.is.supported.only.for.final.fields", REFACTORING_NAME);
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_FIELD, project);
      return;
    }

    if (!field.hasInitializer()) {
      String message = RefactoringBundle.message("no.initializer.present.for.the.field");
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_FIELD, project);
      return;
    }

    if (ReferencesSearch.search(field, project.getProjectScope(), false).findFirst() == null) {
      String message = RefactoringBundle.message("field.0.is.never.used", field.getName());
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_FIELD, project);
      return;
    }

    PsiReference reference = editor != null ? TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset()) : null;
    if (reference != null && !field.equals(reference.resolve())) {
      reference = null;
    }

    final boolean invokedOnReference = (reference != null);
    if (!invokedOnReference) {
      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, field)) return;
    }
    PsiReferenceExpression refExpression = reference instanceof PsiReferenceExpression ? (PsiReferenceExpression)reference : null;
    InlineFieldDialog dialog = new InlineFieldDialog(project, field, refExpression);
    dialog.show();
  }
}
