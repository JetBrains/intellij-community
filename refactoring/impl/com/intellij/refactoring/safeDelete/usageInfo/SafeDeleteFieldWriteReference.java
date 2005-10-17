package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.psi.*;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author dsl
 */
public class SafeDeleteFieldWriteReference extends SafeDeleteReferenceUsageInfo {
  private final PsiAssignmentExpression myExpression;

  public SafeDeleteFieldWriteReference(PsiAssignmentExpression expr, PsiField referencedElement) {
    super(expr, referencedElement, safeRemoveRHS(expr));
    myExpression = expr;
  }

  private static boolean safeRemoveRHS(PsiAssignmentExpression expression) {
    final PsiExpression rExpression = expression.getRExpression();
    final PsiElement parent = expression.getParent();
    return RefactoringUtil.verifySafeCopyExpression(rExpression) == RefactoringUtil.EXPR_COPY_SAFE
                            && parent instanceof PsiExpressionStatement
                            && ((PsiExpressionStatement) parent).getExpression() == expression;
  }

  public void deleteElement() throws IncorrectOperationException {
    myExpression.getParent().delete();
  }

}
