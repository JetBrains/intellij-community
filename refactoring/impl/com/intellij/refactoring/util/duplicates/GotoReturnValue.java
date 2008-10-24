/*
 * User: anna
 * Date: 24-Oct-2008
 */
package com.intellij.refactoring.util.duplicates;

import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public abstract class GotoReturnValue implements ReturnValue {
  @Nullable
  public PsiStatement createReplacement(final PsiMethod extractedMethod, final PsiMethodCallExpression methodCallExpression) throws
                                                                                                                             IncorrectOperationException {
    if (!TypeConversionUtil.isBooleanType(extractedMethod.getReturnType())) return null;
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(methodCallExpression.getProject()).getElementFactory();
    final PsiIfStatement statement = (PsiIfStatement)elementFactory.createStatementFromText(getGotoStatement(), null);
    final PsiExpression condition = statement.getCondition();
    assert condition != null;
    condition.replace(methodCallExpression);
    return (PsiStatement)statement.getManager().getCodeStyleManager().reformat(statement);
  }

  @NonNls
  public abstract String getGotoStatement();
}