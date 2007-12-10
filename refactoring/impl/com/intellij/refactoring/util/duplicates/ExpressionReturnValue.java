/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring.util.duplicates;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;

/**
 * @author dsl
 */
public class ExpressionReturnValue implements ReturnValue {
  private final PsiExpression myExpression;

  public ExpressionReturnValue(PsiExpression expression) {
    myExpression = expression;
  }

  public PsiExpression getExpression() {
    return myExpression;
  }

  public boolean isEquivalent(ReturnValue other) {
    if (!(other instanceof ExpressionReturnValue)) return false;
    return PsiEquivalenceUtil.areElementsEquivalent(myExpression, ((ExpressionReturnValue)other).myExpression);
  }

  public PsiStatement createReplacement(final PsiMethodCallExpression methodCallExpression)
    throws IncorrectOperationException {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(methodCallExpression.getProject()).getElementFactory();
    final CodeStyleManager styleManager = CodeStyleManager.getInstance(methodCallExpression.getProject());
    PsiExpressionStatement expressionStatement;
    expressionStatement = (PsiExpressionStatement)elementFactory.createStatementFromText("x = y();", null);
    expressionStatement = (PsiExpressionStatement)styleManager.reformat(expressionStatement);
    final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expressionStatement.getExpression();
    assignmentExpression.getLExpression().replace(getExpression());
    assignmentExpression.getRExpression().replace(methodCallExpression);
    return expressionStatement;
  }
}
