/**
 * @author cdr
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.impl.quickfix.SimplifyBooleanExpressionFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

public class SimplifyBooleanExpressionAction implements IntentionAction{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.SimplifyBooleanExpressionAction");

  public String getText() {
    return getFamilyName();
  }

  public String getFamilyName() {
    return new SimplifyBooleanExpressionFix(null,null).getFamilyName();
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    try {
      final PsiExpression newExpression = simplifyExpression(editor, file, false);
      return newExpression != null;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return false;
  }

  private static PsiExpression simplifyExpression(final Editor editor, final PsiFile file, boolean replace) throws IncorrectOperationException {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;
    PsiExpression expression = PsiTreeUtil.getParentOfType(element, PsiExpression.class);
    if (expression == null) return null;
    while (expression.getParent() instanceof PsiExpression) {
      expression = (PsiExpression)expression.getParent();
    }
    final PsiExpression newExpression = SimplifyBooleanExpressionFix.canBeSimplified(expression);
    return replace ? (PsiExpression)expression.replace(newExpression) : newExpression;
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;
    simplifyExpression(editor, file, true);
  }

  public boolean startInWriteAction() {
    return true;
  }
}