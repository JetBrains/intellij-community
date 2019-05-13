// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * User : catherine
 */
public class ListCreationQuickFix implements LocalQuickFix {
  private final SmartPsiElementPointer<PyAssignmentStatement> myStatement;
  private final List<PyExpressionStatement> myStatements = new ArrayList<>();

  public ListCreationQuickFix(PyAssignmentStatement statement) {
    myStatement = SmartPointerManager.createPointer(statement);
  }

  public void addStatement(PyExpressionStatement statement) {
    myStatements.add(statement);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("QFIX.list.creation");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    StringBuilder stringBuilder = new StringBuilder();
    final PyAssignmentStatement assignmentStatement = myStatement.getElement();
    if (assignmentStatement == null) return;
    final PyExpression assignedValue = assignmentStatement.getAssignedValue();
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
