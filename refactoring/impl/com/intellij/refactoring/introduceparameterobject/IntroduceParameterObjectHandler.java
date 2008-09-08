package com.intellij.refactoring.introduceparameterobject;

import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;

public class IntroduceParameterObjectHandler implements RefactoringActionHandler {
  private static final String REFACTORING_NAME = RefactorJBundle.message("introduce.parameter.object");

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    final ScrollingModel scrollingModel = editor.getScrollingModel();
    scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE);
    final PsiElement element = LangDataKeys.PSI_ELEMENT.getData(dataContext);
    PsiMethod selectedMethod = null;
    if (element instanceof PsiMethod) {
      selectedMethod = (PsiMethod)element;
    }
    else {
      final CaretModel caretModel = editor.getCaretModel();
      final int position = caretModel.getOffset();
      PsiElement selectedElement = file.findElementAt(position);
      while (selectedElement != null) {
        if (selectedElement instanceof PsiMethod) {
          selectedMethod = (PsiMethod)selectedElement;
          break;
        }
        selectedElement = selectedElement.getParent();
      }
    }
    if (selectedMethod == null) {
      final String message = RefactorJBundle.message("cannot.perform.the.refactoring") +
                             RefactorJBundle.message("the.caret.should.be.positioned.at.the.name.of.the.method.to.be.refactored");
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.IntroduceParameterObject);
      return;
    }
    invoke(project, selectedMethod, editor);
  }

  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    if (elements.length != 1) {
      return;
    }
    final PsiMethod method = PsiTreeUtil.getParentOfType(elements[0], PsiMethod.class, false);
    if (method == null) {
      return;
    }
    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    invoke(project, method, editor);
  }

  private static void invoke(final Project project, final PsiMethod selectedMethod, Editor editor) {
    PsiMethod newMethod = SuperMethodWarningUtil.checkSuperMethod(selectedMethod, RefactoringBundle.message("to.refactor"));
    if (newMethod == null) return;
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, newMethod)) return;

    final PsiParameter[] parameters = newMethod.getParameterList().getParameters();
    if (parameters.length == 0) {
      final String message =
        RefactorJBundle.message("cannot.perform.the.refactoring") + RefactorJBundle.message("method.selected.has.no.parameters");
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.IntroduceParameterObject);
      return;
    }
    if (newMethod instanceof PsiCompiledElement) {
      CommonRefactoringUtil.showErrorHint(project, editor, RefactorJBundle.message("cannot.perform.the.refactoring") + RefactorJBundle.message(
          "the.selected.method.cannot.be.wrapped.because.it.is.defined.in.a.non.project.class"), REFACTORING_NAME, HelpID.IntroduceParameterObject);
      return;
    }
    new IntroduceParameterObjectDialog(newMethod).show();
  }
}
