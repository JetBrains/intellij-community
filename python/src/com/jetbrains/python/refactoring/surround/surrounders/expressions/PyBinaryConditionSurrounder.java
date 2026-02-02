// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.surround.surrounders.expressions;

import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementListContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PyBinaryConditionSurrounder extends PyExpressionAsConditionSurrounder {

  private final String myTextToGenerate;

  public PyBinaryConditionSurrounder(@NotNull String textToGenerate) {
    myTextToGenerate = textToGenerate;
  }

  @Override
  protected String getTextToGenerate() {
    return myTextToGenerate;
  }

  @Override
  protected @Nullable PyExpression getCondition(PyStatement statement) {
    if (statement instanceof PyIfStatement ifStatement) {
      PyExpression condition = ifStatement.getIfPart().getCondition();
      if (condition == null) {
        return null;
      }
      return ((PyBinaryExpression)condition).getLeftExpression();
    }
    return null;
  }

  @Override
  protected @Nullable PyStatementListContainer getStatementListContainer(PyStatement statement) {
    if (statement instanceof PyIfStatement) {
      return ((PyIfStatement)statement).getIfPart();
    }
    return null;
  }
}
