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
public class ReturnStatementReturnValue implements ReturnValue {
  public static final ReturnStatementReturnValue INSTANCE = new ReturnStatementReturnValue();

  private ReturnStatementReturnValue() {}

  public boolean isEquivalent(ReturnValue other) {
    return other instanceof ReturnStatementReturnValue;
  }

  public PsiStatement createReplacement(final PsiMethodCallExpression methodCallExpression) throws IncorrectOperationException {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(methodCallExpression.getProject()).getElementFactory();
    final CodeStyleManager styleManager = CodeStyleManager.getInstance(methodCallExpression.getProject());
    PsiReturnStatement returnStatement = (PsiReturnStatement)elementFactory.createStatementFromText("return x;", null);
    returnStatement = (PsiReturnStatement) styleManager.reformat(returnStatement);
    returnStatement.getReturnValue().replace(methodCallExpression);
    return returnStatement;
  }
}
