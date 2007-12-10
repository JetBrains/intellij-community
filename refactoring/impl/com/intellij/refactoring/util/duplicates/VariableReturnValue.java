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
public class VariableReturnValue implements ReturnValue {
  private final PsiVariable myVariable;

  public VariableReturnValue(PsiVariable variable) {
    myVariable = variable;
  }

  public boolean isEquivalent(ReturnValue other) {
    if (!(other instanceof VariableReturnValue)) return false;
    return myVariable == ((VariableReturnValue)other).myVariable;
  }

  public PsiVariable getVariable() {
    return myVariable;
  }

  public PsiStatement createReplacement(final PsiMethodCallExpression methodCallExpression) throws IncorrectOperationException {
    final PsiDeclarationStatement statement;

    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(methodCallExpression.getProject()).getElementFactory();
    final CodeStyleManager styleManager = CodeStyleManager.getInstance(methodCallExpression.getProject());
    statement = (PsiDeclarationStatement)styleManager.reformat(
      elementFactory.createVariableDeclarationStatement(myVariable.getName(), myVariable.getType(), methodCallExpression)
    );
    ((PsiVariable)statement.getDeclaredElements()[0]).getModifierList().replace(myVariable.getModifierList());
    return statement;
  }
}
