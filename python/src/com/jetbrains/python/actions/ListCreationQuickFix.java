package com.jetbrains.python.actions;

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
  private final List<PyExpressionStatement> myStatements = new ArrayList<PyExpressionStatement>();

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
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    StringBuilder stringBuilder = new StringBuilder();
    for (PyExpression expression : ((PyListLiteralExpression) myStatement.getAssignedValue()).getElements()) {
      stringBuilder.append(expression.getText()).append(", ");
    }
    for (PyExpressionStatement statement: myStatements) {
      for (PyExpression expr : ((PyCallExpression)statement.getExpression()).getArguments())
        stringBuilder.append(expr.getText()).append(", ");
      statement.delete();
    }
    myStatement.getAssignedValue().replace(
      elementGenerator.createExpressionFromText("[" + stringBuilder.substring(0, stringBuilder.length() - 2) + "]"));
  }
}
