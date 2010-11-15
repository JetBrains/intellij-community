package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * User: catherine
 */
public class RedundantParenthesesQuickFix implements LocalQuickFix {
  private final PyStatementWithElse myStatement;
  private final List<PyIfPart> myStatements = new ArrayList<PyIfPart>();

  public RedundantParenthesesQuickFix(PyStatementWithElse statement) {
    myStatement = statement;
  }

  public void addStatement(PyIfPart statement) {
    myStatements.add(statement);
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.redundant.parentheses");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PyExpression condition = null;
    if (myStatement instanceof PyIfStatement) {
      condition = ((PyIfStatement)myStatement).getIfPart().getCondition();
    }
    else if (myStatement instanceof PyWhileStatement) {
      condition = ((PyWhileStatement)myStatement).getWhilePart().getCondition();
    }
    replaceCondition(condition);
    for (PyIfPart ifPart : myStatements) {
      replaceCondition(ifPart.getCondition());
    }
  }

  private static void replaceCondition(PsiElement condition) {
    if (null != condition) {
      while (condition instanceof PyParenthesizedExpression) {
        PyExpression expression = ((PyParenthesizedExpression)condition).getContainedExpression();
        if (expression != null) {
          condition = condition.replace(expression);
        }
      }
    }
  }
}
