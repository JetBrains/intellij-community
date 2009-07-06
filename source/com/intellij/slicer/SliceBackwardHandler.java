package com.intellij.slicer;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author cdr
 */
public class SliceBackwardHandler implements CodeInsightActionHandler {
  public void invoke(final Project project, final Editor editor, final PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments(); // prevents problems with smart pointers creation
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

    SliceManager sliceManager = SliceManager.getInstance(project);
    sliceManager.slice(expression);
  }

  @Nullable
  public static PsiElement getExpressionAtCaret(final Editor editor, final PsiFile file) {
    int offset = TargetElementUtilBase.adjustOffset(editor.getDocument(), editor.getCaretModel().getOffset());
    if (offset != 0) return findParentElement(file.findElementAt(offset));
    return null;
  }

  @Nullable
  private static PsiElement findParentElement(PsiElement element) {
    return PsiTreeUtil.getParentOfType(element, PsiExpression.class, PsiParameter.class, PsiField.class);
  }
}
