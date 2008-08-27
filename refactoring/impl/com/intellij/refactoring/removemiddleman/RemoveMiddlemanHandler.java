package com.intellij.refactoring.removemiddleman;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.RefactorJHelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class RemoveMiddlemanHandler implements RefactoringActionHandler {
  private static final String REFACTORING_NAME = RefactorJBundle.message("remove.middleman");
  @NonNls static final String REMOVE_METHODS = "refactoring.removemiddleman.remove.methods";

  protected static String getRefactoringName() {
    return REFACTORING_NAME;
  }

  protected static String getHelpID() {
    return RefactorJHelpID.RemoveMiddleman;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    final ScrollingModel scrollingModel = editor.getScrollingModel();
    scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE);
    final PsiElement element = LangDataKeys.PSI_ELEMENT.getData(dataContext);
    if (!(element instanceof PsiField)) {
      CommonRefactoringUtil.showErrorMessage(null, RefactorJBundle.message("cannot.perform.the.refactoring") +
                                                   RefactorJBundle
                                                     .message("the.caret.should.be.positioned.at.the.name.of.the.field.to.be.refactored"),
                                             getHelpID(), project);
      return;
    }
    invoke((PsiField)element);
  }

  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    if (elements.length != 1) {
      return;
    }
    if (elements[0] instanceof PsiField) {
      invoke((PsiField)elements[0]);
    }
  }

  private static void invoke(final PsiField field) {
    final Project project = field.getProject();
    if (!DelegationUtils.fieldUsedAsDelegate(field)) {
      final String message =
        RefactorJBundle.message("cannot.perform.the.refactoring") + RefactorJBundle.message("field.selected.is.not.used.as.a.delegate");
      CommonRefactoringUtil.showErrorMessage(null, message, getHelpID(), project);
      return;
    }

    new RemoveMiddlemanDialog(field, PropertiesComponent.getInstance(project).isTrueValue(REMOVE_METHODS)).show();
  }
}
