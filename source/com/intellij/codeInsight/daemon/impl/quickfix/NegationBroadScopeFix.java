package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author cdr
 * if (!a == b) ...  =>  if (!(a == b)) ...
 */

public class NegationBroadScopeFix implements IntentionAction {
  private final PsiPrefixExpression myPrefixExpression;

  public NegationBroadScopeFix(PsiPrefixExpression prefixExpression) {
    myPrefixExpression = prefixExpression;
  }

  public String getText() {
    String text = "Change to '!(";
    text += myPrefixExpression.getOperand().getText();
    text += " ";
    final PsiElement parent = myPrefixExpression.getParent();
    final String operation = parent instanceof PsiInstanceOfExpression ? "instanceof" : ((PsiBinaryExpression)parent).getOperationSign().getText();
    text += operation + " ";

    final String rop = parent instanceof PsiInstanceOfExpression ? ((PsiInstanceOfExpression)parent).getCheckType().getText()
      : ((PsiBinaryExpression)parent).getROperand().getText();

    text += rop;
    text += ")'";
    return text;
  }

  public String getFamilyName() {
    return null;
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    if (myPrefixExpression == null || !myPrefixExpression.isValid()) return false;

    final PsiElement parent = myPrefixExpression.getParent();
    if (parent instanceof PsiInstanceOfExpression && ((PsiInstanceOfExpression)parent).getOperand() == myPrefixExpression) {
      return true;
    }
    if (!(parent instanceof PsiBinaryExpression)) return false;
    final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)parent;
    return binaryExpression.getLOperand() == myPrefixExpression && TypeConversionUtil.isBooleanType(binaryExpression.getType());
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiExpression operand = myPrefixExpression.getOperand();
    final PsiElement unnegated = myPrefixExpression.replace(operand);
    final PsiElement parent = unnegated.getParent();
    final PsiElementFactory factory = file.getManager().getElementFactory();

    final PsiPrefixExpression negated = (PsiPrefixExpression)factory.createExpressionFromText("!(xxx)", parent);
    final PsiParenthesizedExpression parentheses = (PsiParenthesizedExpression)negated.getOperand();
    parentheses.getExpression().replace(parent.copy());
    parent.replace(negated);
  }

  public boolean startInWriteAction() {
    return true;
  }
}
