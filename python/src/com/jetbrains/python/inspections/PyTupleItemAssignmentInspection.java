package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   01.04.2010
 * Time:   22:02:34
 */
public class PyTupleItemAssignmentInspection extends LocalInspectionTool {
  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.tuple.item.assignment");
  }

  @NotNull
  @Override
  public String getShortName() {
    return "PyTupleItemAssignmentInspection";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
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
      PyExpression[] targets = node.getTargets();
      if (targets.length == 1 && targets[0] instanceof PySubscriptionExpression) {
        PySubscriptionExpression subscriptionExpression = (PySubscriptionExpression)targets[0];
        if (subscriptionExpression.getOperand() instanceof PyReferenceExpression) {
          PyReferenceExpression referenceExpression = (PyReferenceExpression)subscriptionExpression.getOperand();
          PsiElement element = referenceExpression.followAssignmentsChain().getElement();
          if (element instanceof PyExpression) {
            PyExpression expression = (PyExpression)element;
            PyType type = expression.getType();
            if (type != null && "tuple".equals(type.getName())) {
              registerProblem(node, "Tuples doesn't support item assignment");
            }
          }
        }
      }
    }
  }
}
