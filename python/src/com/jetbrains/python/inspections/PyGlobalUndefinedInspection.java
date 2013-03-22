package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyGlobalStatement;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: ktisha
 *
 * pylint W0601
 */
public class PyGlobalUndefinedInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.global.undefined");
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
    public void visitPyGlobalStatement(PyGlobalStatement node) {
      final PyTargetExpression[] globals = node.getGlobals();

      for (PyTargetExpression global : globals) {
        if (global.getReference().resolve() == global) {
          registerProblem(global, PyBundle.message("INSP.NAME.global.$0.undefined", global.getName()));
        }
      }
    }
  }
}
