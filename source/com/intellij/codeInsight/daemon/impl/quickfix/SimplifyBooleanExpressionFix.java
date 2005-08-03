/**
 * @author cdr
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;

public class SimplifyBooleanExpressionFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.SimplifyBooleanExpression");

  private final PsiExpression mySubExpression;
  private final Boolean mySubExpressionValue;
  private PsiExpression trueExpression;
  private PsiExpression falseExpression;

  // subExpressionValue == Boolean.TRUE or Boolean.FALSE if subExpression evaluates to boolean constant and needs to be replaced
  //   otherwise subExpressionValue= null and we starting to simplify expression without any further knowledge
  public SimplifyBooleanExpressionFix(PsiExpression subExpression, Boolean subExpressionValue) {
    mySubExpression = subExpression;
    mySubExpressionValue = subExpressionValue;
  }

  public String getText() {
    return "Simplify '" + mySubExpression.getText() + "' to " + mySubExpressionValue;
  }

  public String getFamilyName() {
    return "Simplify boolean expression";
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return mySubExpression.isValid()
           && mySubExpression.getManager().isInProject(mySubExpression)
           && !PsiUtil.isAccessedForWriting(mySubExpression)
      ;
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!isAvailable(project, editor, file)) return;
    LOG.assertTrue(mySubExpression.isValid());
    PsiExpression expression;
    if (mySubExpressionValue == null) {
      expression = mySubExpression;
    }
    else {
      PsiExpression constExpression = PsiManager.getInstance(project).getElementFactory()
          .createExpressionFromText(mySubExpressionValue.toString(), mySubExpression);
      LOG.assertTrue(constExpression.isValid());
      expression = (PsiExpression)mySubExpression.replace(constExpression);
    }
    while (expression.getParent() instanceof PsiExpression) {
      expression = (PsiExpression)expression.getParent();
    }
    PsiExpression newExpression = simplifyExpression(expression);
    expression.replace(newExpression);
  }

  public PsiExpression simplifyExpression(PsiExpression expression) throws IncorrectOperationException {
    trueExpression = expression.getManager().getElementFactory().createExpressionFromText(Boolean.toString(true), null);
    falseExpression = expression.getManager().getElementFactory().createExpressionFromText(Boolean.toString(false), null);
    final PsiExpression[] copy = new PsiExpression[]{(PsiExpression)expression.copy()};
    copy[0].accept(new PsiRecursiveElementVisitor() {
      public void visitExpression(PsiExpression expression) {
        super.visitExpression(expression);
        ExpressionVisitor expressionVisitor = new ExpressionVisitor();
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
      PsiExpression lOperand = expression.getLOperand();
      PsiExpression rOperand = expression.getROperand();
      PsiJavaToken operationSign = expression.getOperationSign();
      IElementType tokenType = operationSign.getTokenType();
      Boolean lConstBoolean = getConstBoolean(lOperand);
      Boolean rConstBoolean = getConstBoolean(rOperand);

      if (lConstBoolean != null) {
        simplifyBinary(tokenType, lConstBoolean, rOperand);
      }
      else if (rConstBoolean != null) {
        simplifyBinary(tokenType, rConstBoolean, lOperand);
      }
    }

    private void simplifyBinary(IElementType tokenType, Boolean lConstBoolean, PsiExpression rOperand) {
      if (JavaTokenType.ANDAND == tokenType || JavaTokenType.AND == tokenType) {
        resultExpression = lConstBoolean.booleanValue() ? rOperand : falseExpression;
      }
      else if (JavaTokenType.OROR == tokenType || JavaTokenType.OR == tokenType) {
        resultExpression = lConstBoolean.booleanValue() ? trueExpression : rOperand;
      }
      else if (JavaTokenType.EQEQ == tokenType) {
        simplifyEquation(lConstBoolean, rOperand);
      }
      else if (JavaTokenType.NE == tokenType) {
        PsiPrefixExpression negatedExpression = createNegatedExpression(rOperand);
        resultExpression = negatedExpression;
        visitPrefixExpression(negatedExpression);
        simplifyEquation(lConstBoolean, resultExpression);
      }
    }

    private void simplifyEquation(Boolean constBoolean, PsiExpression otherOperand) {
      if (constBoolean.booleanValue()) {
        resultExpression = otherOperand;
      }
      else {
        PsiPrefixExpression negated = createNegatedExpression(otherOperand);
        resultExpression = negated;
        visitPrefixExpression(negated);
      }
    }

    public void visitConditionalExpression(PsiConditionalExpression expression) {
      Boolean condition = getConstBoolean(expression.getCondition());
      if (condition == null) return;
      resultExpression = condition.booleanValue() ? expression.getThenExpression() : expression.getElseExpression();
    }

    private PsiPrefixExpression createNegatedExpression(PsiExpression otherOperand)  {
      try {
        return (PsiPrefixExpression)otherOperand.getManager().getElementFactory()
            .createExpressionFromText("!(" + otherOperand.getText()+")", otherOperand);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
      return null;
    }

    public void visitPrefixExpression(PsiPrefixExpression expression) {
      PsiExpression operand = expression.getOperand();
      Boolean constBoolean = getConstBoolean(operand);
      if (constBoolean == null) return;
      PsiJavaToken operationSign = expression.getOperationSign();
      IElementType tokenType = operationSign.getTokenType();
      if (JavaTokenType.EXCL == tokenType) {
        resultExpression = constBoolean.booleanValue() ? falseExpression : trueExpression;
      }
    }


    public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
      PsiExpression subexpr = expression.getExpression();
      Boolean constBoolean = getConstBoolean(subexpr);
      if (constBoolean == null) return;
      resultExpression = constBoolean.booleanValue() ? trueExpression : falseExpression;
    }

    public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }
  }

  public static Boolean getConstBoolean(PsiExpression operand) {
    if (operand == null) return null;
    String text = operand.getText();
    return "true".equals(text) ? Boolean.TRUE : "false".equals(text) ? Boolean.FALSE : null;
  }

  public static PsiExpression canBeSimplified(PsiExpression expression) {
    try {
      SimplifyBooleanExpressionFix fix = new SimplifyBooleanExpressionFix(expression, null);
      PsiExpression newExpression = fix.simplifyExpression(expression);
      if (Comparing.strEqual(newExpression.getText(), expression.getText())) return null;
      return newExpression;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  public boolean startInWriteAction() {
    return true;
  }
}