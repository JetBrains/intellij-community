/**
 * @author cdr
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.daemon.impl.quickfix.SimplifyBooleanExpressionFix;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

public class SimplifyBooleanExpressionAction implements IntentionAction{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.SimplifyBooleanExpressionAction");

  public String getText() {
    return getFamilyName();
  }

  public String getFamilyName() {
    return new SimplifyBooleanExpressionFix(null,false).getFamilyName();
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
    final Boolean constBoolean = SimplifyBooleanExpressionFix.getConstBoolean(expression);
    if (constBoolean == null) return null;
    PsiExpression topexpression = expression;
    while (topexpression.getParent() instanceof PsiExpression) {
      topexpression = (PsiExpression)topexpression.getParent();
    }
    if (topexpression == expression) return null;
    final SimplifyBooleanExpressionFix fix = new SimplifyBooleanExpressionFix(topexpression, constBoolean.booleanValue());
    final PsiExpression newExpression = fix.simplifyExpression(topexpression);
    if (Comparing.strEqual(newExpression.getText(), topexpression.getText())) return null;
    return replace ? (PsiExpression)topexpression.replace(newExpression) : newExpression;
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    simplifyExpression(editor, file, true);
  }

  public boolean startInWriteAction() {
    return true;
  }
}