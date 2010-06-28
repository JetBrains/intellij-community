package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.actions.DictCreationQuickFix;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexey.Ivanov
 */
public class PyDictCreationInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.dict.creation");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new Visitor(holder);
  }

  private static class Visitor extends PyInspectionVisitor {

    public Visitor(final ProblemsHolder holder) {
      super(holder);
    }

    @Override
    public void visitPyAssignmentStatement(PyAssignmentStatement node) {
      if (node.getAssignedValue() instanceof PyDictLiteralExpression) {
        if (node.getTargets().length != 1) {
          return;
        }
        String name = node.getTargets()[0].getName();
        if (name == null) {
          return;
        }

        PyStatement statement = PsiTreeUtil.getNextSiblingOfType(node, PyStatement.class);
        DictCreationQuickFix quickFix = null;
        boolean availableFix = false;

loop:
        while (statement instanceof PyAssignmentStatement) {
          PyAssignmentStatement assignmentStatement = (PyAssignmentStatement)statement;
          for (Pair<PyExpression, PyExpression> targetToValue : assignmentStatement.getTargetsToValuesMapping()) {
            if (targetToValue.first instanceof PySubscriptionExpression) {
              PySubscriptionExpression subscriptionExpression = (PySubscriptionExpression)targetToValue.first;
              if (name.equals(subscriptionExpression.getOperand().getName()) && subscriptionExpression.getIndexExpression() != null) {
                if (!availableFix) {
                  quickFix = new DictCreationQuickFix(node);
                  availableFix = true;
                }
                continue;
              }
            }
            break loop;
          }

          if (quickFix == null) {
            return;
          }
          quickFix.addStatement(assignmentStatement);
          statement = PsiTreeUtil.getNextSiblingOfType(assignmentStatement, PyStatement.class);
        }
        
        if (availableFix) {
          registerProblem(node, "This dictionary creation could be rewritten by dictionary literal", quickFix);
        }
      }
    }
  }
}
