package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.console.PydevConsoleRunner;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexey.Ivanov
 */
public class PyStatementEffectInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.statement.effect");
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
    public void visitPyExpressionStatement(final PyExpressionStatement node) {
      if (PydevConsoleRunner.isInPydevConsole(node)) {
        return;
      }
      final PyExpression expression = node.getExpression();
      if (expression instanceof PyCallExpression || expression instanceof PyYieldExpression) {
        return;
      }

      if (expression instanceof PyStringLiteralExpression) {
        final PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(expression, PyDocStringOwner.class);
        if (docStringOwner != null) {
          if (docStringOwner.getDocStringExpression() == expression) {
            return;
          }
        }
      }
      else if (expression instanceof PyListCompExpression) {
        if (((PyListCompExpression)expression).getResultExpression() instanceof PyCallExpression) {
          return;
        }
      }
      else if (expression instanceof PyBinaryExpression) {
        PyBinaryExpression binary = (PyBinaryExpression)expression;
        String method = binary.getOperator().getSpecialMethodName();
        if (method != null) {
          // maybe the op is overridden and may produce side effects, like cout << "hello"
          PyType type = binary.getLeftExpression().getType(TypeEvalContext.fast());
          if (type != null && ! type.isBuiltin() && type.resolveMember(method, AccessDirection.READ) != null) {
            return;
          }
          final PyExpression rhs = binary.getRightExpression();
          if (rhs != null) {
            type = rhs.getType(TypeEvalContext.fast());
            if (type != null) {
              String rmethod = "__r" + method.substring(2); // __add__ -> __radd__
              if (! type.isBuiltin() && type.resolveMember(rmethod, AccessDirection.READ) != null) {
                return;
              }
            }
          }
        }
      }

      final PyTryPart tryPart = PsiTreeUtil.getParentOfType(node, PyTryPart.class);
      if (tryPart != null) {
        final PyStatementList statementList = tryPart.getStatementList();
        if (statementList == null) {
          return;
        }
        if (statementList.getStatements().length == 1 && statementList.getStatements()[0] == node) {
          return;
        }
      }
      registerProblem(expression, "Statement seems to have no effect");
    }
  }
}
