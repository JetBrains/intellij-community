package com.jetbrains.python.actions;

import com.google.common.collect.Lists;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: 26.02.2010
 * Time: 13:29:02
 */
public class DictCreationQuickFix implements LocalQuickFix {
  private final PyAssignmentStatement myStatement;
  private final List<PyAssignmentStatement> myStatements = new ArrayList<PyAssignmentStatement>();

  public DictCreationQuickFix(PyAssignmentStatement statement) {
    myStatement = statement;
  }

  public void addStatement(PyAssignmentStatement statement) {
    myStatements.add(statement);
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.dict.creation");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    List<String> statements = Lists.newArrayList();
    for (PyExpression expression: ((PyDictLiteralExpression) myStatement.getAssignedValue()).getElements()) {
      statements.add(expression.getText());
    }


    for (PyAssignmentStatement statement: myStatements) {
      for (Pair<PyExpression, PyExpression> targetToValue : statement.getTargetsToValuesMapping()) {
        PySubscriptionExpression target = (PySubscriptionExpression)targetToValue.first;
        PyExpression indexExpression = target.getIndexExpression();
        assert indexExpression != null;
        statements.add(indexExpression.getText() + ": " + targetToValue.second.getText());
      }
      statement.delete();
    }

    myStatement.getAssignedValue().replace(elementGenerator.createExpressionFromText(LanguageLevel.forElement(myStatement),
                                "{" + StringUtil.join(statements, ", ") + "}"));
  }
}
