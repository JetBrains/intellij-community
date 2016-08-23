/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * User : catherine
 */
public class ListCreationQuickFix implements LocalQuickFix {
  private final PyAssignmentStatement myStatement;
  private final List<PyExpressionStatement> myStatements = new ArrayList<>();

  public ListCreationQuickFix(PyAssignmentStatement statement) {
    myStatement = statement;
  }

  public void addStatement(PyExpressionStatement statement) {
    myStatements.add(statement);
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.list.creation");
  }

  @NotNull
  public String getFamilyName() {
    return getName();
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    StringBuilder stringBuilder = new StringBuilder();
    final PyExpression assignedValue = myStatement.getAssignedValue();
    if (assignedValue == null) return;

    for (PyExpression expression : ((PyListLiteralExpression)assignedValue).getElements()) {
      stringBuilder.append(expression.getText()).append(", ");
    }
    for (PyExpressionStatement statement: myStatements) {
      for (PyExpression expr : ((PyCallExpression)statement.getExpression()).getArguments())
        stringBuilder.append(expr.getText()).append(", ");
      statement.delete();
    }
    assignedValue.replace(
      elementGenerator.createExpressionFromText("[" + stringBuilder.substring(0, stringBuilder.length() - 2) + "]"));
  }
}
