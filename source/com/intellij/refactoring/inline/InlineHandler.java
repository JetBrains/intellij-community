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
import com.intellij.psi.jsp.JspFile;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.lang.jsp.inlineInclude.InlineIncludeFileHandler;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.ide.DataManager;

public class InlineHandler implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inline.InlineHandler");
  private static final String REFACTORING_NAME = RefactoringBundle.message("inline.title");

  public void invoke(Project project, PsiElement[] elements, DataContext dataContext) {
    LOG.assertTrue(elements.length == 1);
    if (dataContext == null) {
      dataContext = DataManager.getInstance().getDataContext();
    }
    final Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
    if (elements[0] instanceof PsiMethod) {
      new InlineMethodHandler().invoke(project, editor, (PsiMethod) elements[0]);
    } else if (elements[0] instanceof  PsiPointcutDef) {
      new InlinePointcutHandler().invoke(project, editor, (PsiPointcutDef) elements[0]);
    } else if (elements[0] instanceof  PsiField) {
      new InlineConstantFieldHandler().invoke(project, editor, (PsiField) elements[0]);
    } else if (elements[0] instanceof PsiLocalVariable) {
      new InlineLocalHandler().invoke(project, editor, (PsiLocalVariable)elements[0]);
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
    } else if (file instanceof JspFile) {
      new InlineIncludeFileHandler().invoke(project, editor, (JspFile)file);;
    } else {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.method.or.local.name"));
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, null, project);
    }
  }
}
