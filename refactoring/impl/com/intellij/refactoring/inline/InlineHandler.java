/**
 * created at Nov 21, 2001
 * @author Jeka
 */
package com.intellij.refactoring.inline;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.lang.jsp.inlineInclude.InlineIncludeFileHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;

public class InlineHandler implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inline.InlineHandler");
  private static final String REFACTORING_NAME = RefactoringBundle.message("inline.title");

  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    LOG.assertTrue(elements.length == 1);
    if (dataContext == null) {
      dataContext = DataManager.getInstance().getDataContext();
    }
    final Editor editor = DataKeys.EDITOR.getData(dataContext);
    if (elements[0] instanceof PsiMethod) {
      InlineMethodHandler.invoke(project, editor, (PsiMethod) elements[0]);
    } else if (elements[0] instanceof  PsiField) {
      InlineConstantFieldHandler.invoke(project, editor, (PsiField) elements[0]);
    } else if (elements[0] instanceof PsiLocalVariable) {
      InlineLocalHandler.invoke(project, editor, (PsiLocalVariable)elements[0], null);
    }
    else if (elements [0] instanceof PsiClass) {
      InlineToAnonymousClassHandler.invoke(project, editor, (PsiClass) elements[0]);
    }
    else {
      LOG.error("Unknown element type to inline:" + elements[0]);
    }
  }

  public void invoke(@NotNull final Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = DataKeys.PSI_ELEMENT.getData(dataContext);
    if (element != null) {
      final com.intellij.lang.refactoring.InlineHandler languageSpecific =
        element.getLanguage().getRefactoringSupportProvider().getInlineHandler();
      if (languageSpecific != null) {
        GenericInlineHandler.invoke(element, editor, languageSpecific);
        return;
      }
    }

    JspFile jspFile;

    if (element instanceof PsiLocalVariable) {
      final PsiReference psiReference = TargetElementUtil.findReference(editor);
      final PsiReferenceExpression refExpr = psiReference instanceof PsiReferenceExpression ? ((PsiReferenceExpression)psiReference) : null;
      InlineLocalHandler.invoke(project, editor, (PsiLocalVariable) element, refExpr);
    } else if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element;
      if (method.isConstructor() && !InlineMethodHandler.isChainingConstructor(method)) {
        InlineToAnonymousClassHandler.invoke(project, editor, method.getContainingClass());
      }
      else {
        InlineMethodHandler.invoke(project, editor, method);
      }
    } else if (element instanceof PsiField) {
      InlineConstantFieldHandler.invoke(project, editor, (PsiField) element);
    }
    else if (element instanceof PsiClass) {
      InlineToAnonymousClassHandler.invoke(project, editor, (PsiClass) element);
    }
    else if (PsiUtil.isInJspFile(file) && (jspFile = PsiUtil.getJspFile(file)) != null) {
      InlineIncludeFileHandler.invoke(project, editor, jspFile);
    }
    else {
      String message =
        RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.method.or.local.name"));
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, null, project);
    }
  }
}
