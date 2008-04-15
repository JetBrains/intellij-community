/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring.util.duplicates;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;

/**
 * @author dsl
 */
public class FieldReturnValue implements ReturnValue {
  private final PsiField myField;

  public FieldReturnValue(PsiField psiField) {
    myField = psiField;
  }

  public boolean isEquivalent(ReturnValue other) {
    if (!(other instanceof FieldReturnValue)) return false;
    return myField == ((FieldReturnValue)other).myField;
  }

  public PsiField getField() {
    return myField;
  }

  public PsiStatement createReplacement(final PsiMethodCallExpression methodCallExpression) throws IncorrectOperationException {

    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(methodCallExpression.getProject()).getElementFactory();
    final CodeStyleManager styleManager = CodeStyleManager.getInstance(methodCallExpression.getProject());
    PsiExpressionStatement expressionStatement;
    expressionStatement = (PsiExpressionStatement)elementFactory.createStatementFromText("x = y();", null);
    expressionStatement = (PsiExpressionStatement)styleManager.reformat(expressionStatement);
    final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expressionStatement.getExpression();
    assignmentExpression.getLExpression().replace(elementFactory.createExpressionFromText(myField.getName(), myField));
    assignmentExpression.getRExpression().replace(methodCallExpression);
    return expressionStatement;

  }
}