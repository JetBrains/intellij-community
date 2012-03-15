package com.jetbrains.python.actions;

import com.google.common.collect.Lists;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.inspections.PyDictCreationInspection;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: 26.02.2010
 * Time: 13:29:02
 */
public class DictCreationQuickFix implements LocalQuickFix {
  private final PyAssignmentStatement myStatement;
  public DictCreationQuickFix(@NotNull final PyAssignmentStatement statement) {
    myStatement = statement;
  }

  @Override
  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.dict.creation");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    final List<String> statements = Lists.newArrayList();
    final PyExpression assignedValue = myStatement.getAssignedValue();
    if (assignedValue instanceof PyDictLiteralExpression) {
      for (PyExpression expression: ((PyDictLiteralExpression)assignedValue).getElements()) {
        statements.add(expression.getText());
      }

      PyStatement statement = PsiTreeUtil.getNextSiblingOfType(myStatement, PyStatement.class);
      while (statement instanceof PyAssignmentStatement) {
        final PyAssignmentStatement assignmentStatement = (PyAssignmentStatement)statement;
        final PyExpression target = myStatement.getTargets()[0];
        final String targetName = target.getName();
        if (targetName != null) {
          final List<Pair<PyExpression, PyExpression>> targetsToValues =
                              PyDictCreationInspection.getDictTargets(target, targetName, assignmentStatement);
          final PyStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(statement, PyStatement.class);
          if (targetsToValues == null || targetsToValues.isEmpty()) break;
          for (Pair<PyExpression, PyExpression> targetToValue : targetsToValues) {
            final PySubscriptionExpression subscription = (PySubscriptionExpression)targetToValue.first;
            final PyExpression indexExpression = subscription.getIndexExpression();
            assert indexExpression != null;
            statements.add(indexExpression.getText() + ": " + targetToValue.second.getText());
            statement.delete();
          }
          statement = nextStatement;
        }
      }

      final PyExpression expression = elementGenerator.createExpressionFromText(LanguageLevel.forElement(myStatement),
                                                                    "{" + StringUtil.join(statements, ", ") + "}");
      if (expression != null)
        assignedValue.replace(expression);
    }
  }
}
