/**
 * @author cdr
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;

public class SimplifyBooleanExpressionFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.SimplifyBooleanExpression");

  private final PsiExpression mySubExpression;
  private final Boolean mySubExpressionValue;

  // subExpressionValue == Boolean.TRUE or Boolean.FALSE if subExpression evaluates to boolean constant and needs to be replaced
  //   otherwise subExpressionValue= null and we starting to simplify expression without any further knowledge
  public SimplifyBooleanExpressionFix(PsiExpression subExpression, Boolean subExpressionValue) {
    mySubExpression = subExpression;
    mySubExpressionValue = subExpressionValue;
  }

  public String getText() {
    return QuickFixBundle.message("simplify.boolean.expression.text", mySubExpression.getText(), mySubExpressionValue);
  }

  public String getFamilyName() {
    return QuickFixBundle.message("simplify.boolean.expression.family");
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
          .createExpressionFromText(Boolean.toString(mySubExpressionValue.booleanValue()), mySubExpression);
      LOG.assertTrue(constExpression.isValid());
      expression = (PsiExpression)mySubExpression.replace(constExpression);
    }
    while (expression.getParent() instanceof PsiExpression) {
      expression = (PsiExpression)expression.getParent();
    }
    PsiExpression newExpression = simplifyExpression(expression);
    expression.replace(newExpression);
  }

  public static PsiExpression simplifyExpression(PsiExpression expression) {
    final PsiExpression[] copy = new PsiExpression[]{(PsiExpression)expression.copy()};
    final ExpressionVisitor expressionVisitor = new ExpressionVisitor(expression.getManager(), true);
    copy[0].accept(new PsiRecursiveElementVisitor() {
      public void visitExpression(PsiExpression expression) {
        super.visitExpression(expression);
        expressionVisitor.clear();
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
  public static boolean canBeSimplified(PsiExpression expression) {
    final ExpressionVisitor expressionVisitor = new ExpressionVisitor(expression.getManager(), false);
    final Ref<Boolean> canBeSimplified = new Ref<Boolean>(Boolean.FALSE);
    expression.accept(new PsiRecursiveElementVisitor() {
      public void visitElement(PsiElement element) {
        if (!canBeSimplified.get().booleanValue()) {
          super.visitElement(element);
        }
      }

      public void visitExpression(PsiExpression expression) {
        super.visitExpression(expression);
        expressionVisitor.clear();
        expression.accept(expressionVisitor);
        if (expressionVisitor.canBeSimplifiedFlag) {
          canBeSimplified.set(Boolean.TRUE);
        }
      }
    });
    return canBeSimplified.get().booleanValue();
  }

  private static class ExpressionVisitor extends PsiElementVisitor {
    private PsiExpression resultExpression;
    private final PsiExpression trueExpression;
    private final PsiExpression falseExpression;
    private final boolean isCreateResult;
    boolean canBeSimplifiedFlag;

    public ExpressionVisitor(PsiManager psiManager, final boolean createResult) {
      isCreateResult = createResult;
      trueExpression = createResult ? createExpression(psiManager, Boolean.toString(true)) : null;
      falseExpression = createResult ? createExpression(psiManager, Boolean.toString(true)) : null;
    }

    private static PsiExpression createExpression(final PsiManager psiManager, String text) {
      try {
        return psiManager.getElementFactory().createExpressionFromText(text, null);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return null;
      }
    }

    private boolean markAndCheckCreateResult() {
      canBeSimplifiedFlag = true;
      return isCreateResult;
    }

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
      if (!markAndCheckCreateResult()) {
        return;
      }
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
      if (!markAndCheckCreateResult()) {
        return;
      }
      resultExpression = condition.booleanValue() ? expression.getThenExpression() : expression.getElseExpression();
    }

    private static PsiPrefixExpression createNegatedExpression(PsiExpression otherOperand)  {
      return (PsiPrefixExpression)createExpression(otherOperand.getManager(), "!(" + otherOperand.getText()+")");
    }

    public void visitPrefixExpression(PsiPrefixExpression expression) {
      PsiExpression operand = expression.getOperand();
      Boolean constBoolean = getConstBoolean(operand);
      if (constBoolean == null) return;
      PsiJavaToken operationSign = expression.getOperationSign();
      IElementType tokenType = operationSign.getTokenType();
      if (JavaTokenType.EXCL == tokenType) {
        if (!markAndCheckCreateResult()) {
          return;
        }
        resultExpression = constBoolean.booleanValue() ? falseExpression : trueExpression;
      }
    }


    public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
      PsiExpression subexpr = expression.getExpression();
      Boolean constBoolean = getConstBoolean(subexpr);
      if (constBoolean == null) return;
      if (!markAndCheckCreateResult()) {
        return;
      }
      resultExpression = constBoolean.booleanValue() ? trueExpression : falseExpression;
    }

    public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }

    public void clear() {
      resultExpression = null;
    }
  }

  public static Boolean getConstBoolean(PsiExpression operand) {
    if (operand == null) return null;
    String text = operand.getText();
    return PsiKeyword.TRUE.equals(text) ? Boolean.TRUE : PsiKeyword.FALSE.equals(text) ? Boolean.FALSE : null;
  }

  public boolean startInWriteAction() {
    return true;
  }
}