package com.intellij.slicer;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import org.jetbrains.annotations.Nullable;

/**
 * @author cdr
 */
public class SliceBackwardHandler implements CodeInsightActionHandler {
  public void invoke(final Project project, final Editor editor, final PsiFile file) {
    PsiElement expression = getExpressionAtCaret(editor, file);
    if (expression == null) {
      HintManager.getInstance().showErrorHint(editor, "Cannot find what to slice. Please stand on the expression or field or method parameter and try again.");
      return;
    }
    slice(expression);
  }

  public boolean startInWriteAction() {
    return false;
  }

  private static void slice(final PsiElement expression) {
    final Project project = expression.getProject();

    PsiDocumentManager.getInstance(project).commitAllDocuments(); // prevents problems with smart pointers creation

    SliceManager sliceManager = SliceManager.getInstance(project);
    sliceManager.slice(expression);
  }

  @Nullable
  private static PsiElement getExpressionAtCaret(final Editor editor, final PsiFile file) {
    PsiElement element = BaseRefactoringAction.getElementAtCaret(editor, file);
    PsiElement parent = findParentElement(element);
    if (parent != null) return parent;
    int offset = editor.getCaretModel().getOffset();
    if (offset != 0) return findParentElement(file.findElementAt(offset - 1));
    return null;
  }

  @Nullable
  private static PsiElement findParentElement(PsiElement element) {
    PsiElement parent = PsiTreeUtil.getParentOfType(element, PsiExpression.class, PsiParameter.class, PsiField.class);
    if (parent instanceof PsiExpression && parent.getParent() instanceof PsiStatement) return null; //no point in slicing statements
    return parent;
  }
}
