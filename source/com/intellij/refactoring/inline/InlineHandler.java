/**
 * created at Nov 21, 2001
 * @author Jeka
 */
package com.intellij.refactoring.inline;

import com.intellij.aspects.psi.PsiPointcutDef;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.util.RefactoringMessageUtil;

public class InlineHandler implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inline.InlineHandler");

  public void invoke(Project project, PsiElement[] elements, DataContext dataContext) {
    LOG.assertTrue(elements.length == 1);
    if (elements[0] instanceof PsiMethod) {
      new InlineMethodHandler().invoke(project, null, (PsiMethod) elements[0]);
    } else if (elements[0] instanceof  PsiPointcutDef) {
      new InlinePointcutHandler().invoke(project, null, (PsiPointcutDef) elements[0]);
    } else if (elements[0] instanceof  PsiField) {
      new InlineConstantFieldHandler().invoke(project, null, (PsiField) elements[0]);
    } else if (elements[0] instanceof PsiLocalVariable) {
      new InlineLocalHandler().invoke(project, null, (PsiLocalVariable)elements[0]);
    } else {
      LOG.error("Unknown element type to inline:" + elements[0]);
    }
  }

  public void invoke(final Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = (PsiElement) dataContext.getData(DataConstants.PSI_ELEMENT);
    if (element instanceof PsiLocalVariable) {
      new InlineLocalHandler().invoke(project, editor, (PsiLocalVariable) element);
    } else if (element instanceof PsiPointcutDef) {
      new InlinePointcutHandler().invoke(project, editor, (PsiPointcutDef) element);
    } else if (element instanceof PsiMethod) {
      new InlineMethodHandler().invoke(project, editor, (PsiMethod) element);
    } else if (element instanceof PsiField) {
      new InlineConstantFieldHandler().invoke(project, editor, (PsiField) element);
    } else {
      String message =
              "Cannot perform the refactoring.\n" +
              "The caret should be positioned at the name of\n" +
              "the method or local variable to be refactored.";
      RefactoringMessageUtil.showErrorMessage("Inline", message, null, project);
    }
  }
}
