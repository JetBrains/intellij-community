package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.actions.PyDefaultArgumentQuickFix;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexey.Ivanov
 */
public class PyDefaultArgumentInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.default.argument");
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
    public void visitPyNamedParameter(PyNamedParameter node) {
      PyExpression defaultValue = node.getDefaultValue();
      if (defaultValue != null) {
        if (defaultValue instanceof PyListLiteralExpression || defaultValue instanceof PyDictLiteralExpression) {
          registerProblem(defaultValue, "Default argument value is mutable", new PyDefaultArgumentQuickFix());
        }
        if (defaultValue instanceof PyCallExpression) {
          PyExpression callee = ((PyCallExpression)defaultValue).getCallee();
          if (callee != null && "dict".equals(callee.getText()))
            registerProblem(defaultValue, "Default argument value is mutable", new PyDefaultArgumentQuickFix());
        }
      }
    }
  }
}
