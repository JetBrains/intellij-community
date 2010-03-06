package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   06.03.2010
 * Time:   14:34:10
 */
public class PyAssigningFunctionCallInspection extends LocalInspectionTool {
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
    return PyBundle.message("INSP.NAME.assigning.function");
  }

  @NotNull
  @Override
  public String getShortName() {
    return "PyAssigningFunctionCallInspection";
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
      if (node.getAssignedValue() instanceof PyCallExpression) {
        PyCallExpression callExpression = (PyCallExpression)node.getAssignedValue();
        PyCallExpression.PyMarkedFunction pyMarkedFunction = callExpression.resolveCallee();
        if (pyMarkedFunction == null) {
          return;
        }
        PyReturnStatement[] returnStatements = PyUtil.getAllChildrenOfType(pyMarkedFunction.getFunction(), PyReturnStatement.class);
        if (returnStatements.length == 0) {
          registerProblem(node, "Assigning to function call which doesn't have return statements");
          return;
        }
        for (PyReturnStatement returnStatement: returnStatements) {
          PyExpression expression = returnStatement.getExpression();
          if (expression instanceof PyReferenceExpression) {
            PyReferenceExpression referenceExpression = (PyReferenceExpression)expression;
            if (!PyNames.NONE.equals(referenceExpression.getReferencedName())) {
              return;
            }
          } else if (expression != null) {
            return;
          }
        }
        registerProblem(node, "Assigning to function which doesn't return value");
      }
    }
  }
}
