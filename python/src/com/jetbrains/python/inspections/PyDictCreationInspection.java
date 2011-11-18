package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.actions.DictCreationQuickFix;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  private static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyAssignmentStatement(PyAssignmentStatement node) {
      if (node.getAssignedValue() instanceof PyDictLiteralExpression) {
        if (node.getTargets().length != 1) {
          return;
        }
        final PyExpression target = node.getTargets()[0];
        String name = target.getName();
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
              if (name.equals(subscriptionExpression.getOperand().getName()) &&
                  subscriptionExpression.getIndexExpression() != null &&
                  !referencesTarget(targetToValue.second, target)) {
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
          registerProblem(node, "This dictionary creation could be rewritten as a dictionary literal", quickFix);
        }
      }
    }

    private boolean referencesTarget(PyExpression expression, final PyExpression target) {
      final List<PsiElement> refs = new ArrayList<PsiElement>();
      expression.accept(new PyRecursiveElementVisitor() {
        @Override
        public void visitPyReferenceExpression(PyReferenceExpression node) {
          super.visitPyReferenceExpression(node);
          final PsiPolyVariantReference ref = node.getReference(resolveWithoutImplicits());
          if (ref.isReferenceTo(target)) {
            refs.add(node);
          }
        }
      });
      return !refs.isEmpty();
    }
  }
}
