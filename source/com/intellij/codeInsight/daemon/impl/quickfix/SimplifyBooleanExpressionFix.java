/**
 * @author cdr
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;

public class SimplifyBooleanExpressionFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.SimplifyBooleanExpression");

  private final PsiExpression mySubExpression;
  private final boolean mySubExpressionValue;
  private PsiExpression trueExpression;
  private PsiExpression falseExpression;

  public SimplifyBooleanExpressionFix(PsiExpression subExpression, boolean subExpressionValue) {
    mySubExpression = subExpression;
    mySubExpressionValue = subExpressionValue;
  }

  public String getText() {
    return getFamilyName();
  }

  public String getFamilyName() {
    return "Simplify boolean expression";
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return mySubExpression.isValid()
          && mySubExpression.getManager().isInProject(mySubExpression);
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiExpression constExpression = mySubExpression.getManager().getElementFactory().createExpressionFromText(Boolean.toString(mySubExpressionValue), mySubExpression);
    PsiExpression expression = (PsiExpression)mySubExpression.replace(constExpression);
    while (expression.getParent() instanceof PsiExpression) {
      expression = (PsiExpression)expression.getParent();
    }
    final PsiExpression newExpression = simplifyExpression(expression);
    expression.replace(newExpression);
  }

  public PsiExpression simplifyExpression(PsiExpression expression) throws IncorrectOperationException {
    trueExpression = expression.getManager().getElementFactory().createExpressionFromText(Boolean.toString(true), null);
    falseExpression = expression.getManager().getElementFactory().createExpressionFromText(Boolean.toString(false), null);
    final PsiExpression[] copy = new PsiExpression[]{(PsiExpression)expression.copy()};
    copy[0].accept(new PsiRecursiveElementVisitor() {
      public void visitElement(PsiElement element) {
        final PsiElement[] children = element.getChildren();
        for (int i = 0; i < children.length; i++) {
          PsiElement child = children[i];
          child.accept(this);
        }
      }

      public void visitExpression(PsiExpression expression) {
        super.visitExpression(expression);
        final ExpressionVisitor expressionVisitor = new ExpressionVisitor();
        expression.accept(expressionVisitor);
        if (expressionVisitor.resultExpression != null) {
          LOG.assertTrue(expressionVisitor.resultExpression.isValid());
          try {
            if (expression != copy[0]) {
              expression.replace(expressionVisitor.resultExpression);
            }
            else {
              copy[0] = expressionVisitor.resultExpression;
            }
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
    });
    return copy[0];
  }

  private class ExpressionVisitor extends PsiElementVisitor {
    private PsiExpression resultExpression;

    public void visitBinaryExpression(PsiBinaryExpression expression) {
      final PsiExpression lOperand = expression.getLOperand();
      final PsiExpression rOperand = expression.getROperand();
      final PsiJavaToken operationSign = expression.getOperationSign();
      final IElementType tokenType = operationSign.getTokenType();
      final Boolean lConstBoolean = getConstBoolean(lOperand);
      final Boolean rConstBoolean = getConstBoolean(rOperand);

      if (lConstBoolean != null) {
        if (JavaTokenType.ANDAND == tokenType || JavaTokenType.AND == tokenType) {
          resultExpression = lConstBoolean.booleanValue() ? rOperand : falseExpression;
        }
        else if (JavaTokenType.OROR == tokenType || JavaTokenType.OR == tokenType) {
          resultExpression = lConstBoolean.booleanValue() ? trueExpression : rOperand;
        }
      }
      else if (rConstBoolean != null) {
        if (JavaTokenType.ANDAND == tokenType || JavaTokenType.AND == tokenType) {
          resultExpression = rConstBoolean.booleanValue() ? lOperand : falseExpression;
        }
        else if (JavaTokenType.OROR == tokenType || JavaTokenType.OR == tokenType) {
          resultExpression = rConstBoolean.booleanValue() ? trueExpression : lOperand;
        }
      }
    }

    public void visitPrefixExpression(PsiPrefixExpression expression) {
      final PsiExpression operand = expression.getOperand();
      final Boolean constBoolean = getConstBoolean(operand);
      if (constBoolean == null) return;
      final PsiJavaToken operationSign = expression.getOperationSign();
      final IElementType tokenType = operationSign.getTokenType();
      if (JavaTokenType.EXCL == tokenType) {
        resultExpression = constBoolean.booleanValue() ? falseExpression : trueExpression;
      }
    }


    public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
      final PsiExpression subexpr = expression.getExpression();
      final Boolean constBoolean = getConstBoolean(subexpr);
      if (constBoolean == null) return;
      resultExpression = constBoolean.booleanValue() ? trueExpression : falseExpression;
    }
  }

  public static Boolean getConstBoolean(final PsiExpression operand) {
    if (operand == null) return null;
    final String text = operand.getText();
    return "true".equals(text) ? Boolean.TRUE : "false".equals(text) ? Boolean.FALSE : null;
  }

  public boolean startInWriteAction() {
    return true;
  }
}