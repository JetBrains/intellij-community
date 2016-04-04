/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.refactoring.surround.surrounders.expressions;

import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyBinaryConditionSurrounder extends PyExpressionAsConditionSurrounder {

  private final String myTextToGenerate;
  private final String myTemplateDescription;

  public PyBinaryConditionSurrounder(@NotNull String textToGenerate, @NotNull String templateDescription) {
    myTextToGenerate = textToGenerate;
    myTemplateDescription = templateDescription;
  }

  @Override
  protected String getTextToGenerate() {
    return myTextToGenerate;
  }

  @Nullable
  @Override
  protected PyExpression getCondition(PyStatement statement) {
    if (statement instanceof PyIfStatement) {
      PyIfStatement ifStatement = (PyIfStatement)statement;
      PyExpression condition = ifStatement.getIfPart().getCondition();
      if (condition == null) {
        return null;
      }
      return ((PyBinaryExpression)condition).getLeftExpression();
    }
    return null;
  }

  @Nullable
  @Override
  protected PyStatementListContainer getStatementListContainer(PyStatement statement) {
    if (statement instanceof PyIfStatement) {
      return ((PyIfStatement)statement).getIfPart();
    }
    return null;
  }

  @Override
  public String getTemplateDescription() {
    return myTemplateDescription;
  }
}
