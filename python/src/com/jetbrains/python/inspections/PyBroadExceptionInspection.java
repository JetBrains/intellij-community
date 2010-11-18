package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * Inspection to detect too broad except clause
 * such as no exception class specified, or specified as 'Exception'
 */
public class PyBroadExceptionInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.too.broad.exception.clauses");
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
    public void visitPyExceptBlock(final PyExceptPart node){
      PyExpression exceptClass= node.getExceptClass();
      if (exceptClass == null) {
        registerProblem(node, "Too broad exception clause");
      }
      if (exceptClass instanceof PyReferenceExpression) {
        PyReferenceExpression exceptClassRef = (PyReferenceExpression)exceptClass;
        if (myTypeEvalContext.getType(exceptClassRef).isBuiltin())
          registerProblem(node, "Too broad exception clause");
      }

    }
  }
}
