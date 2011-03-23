package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.actions.RemoveArgumentEqualDefaultQuickFix;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * Inspection to detect situations, where argument passed to function
 * is equal to default parameter value
 * for instance,
 * dict().get(x, None) --> None is default value for second param in dict().get function
 */
public class PyArgumentEqualDefaultInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.argument.equal.default");
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
    public void visitPyCallExpression(final PyCallExpression node){
      PyExpression[] arguments = node.getArguments();
      PyExpression callee = node.getCallee();
      if (callee != null) {
        PsiReference ref = callee.getReference();
        if (ref != null) {
          PsiElement function = ref.resolve();
          if (function instanceof PyFunction) {
            checkArguments(function, arguments);
          }
        }
      }
    }

    private void checkArguments(PsiElement function, PyExpression[] arguments) {
      int argumentsSize = arguments.length;
      PyClass containingClass = ((PyFunction)function).getContainingClass();
      int adjust = 0;
      if (containingClass != null)
        adjust = 1;
      PyParameter[] params = ((PyFunction)function).getParameterList().getParameters();
      for (int i = 0; i != params.length - adjust; ++i) {
        PyParameter p = params[i+adjust];
        if (p instanceof PyNamedParameter) {
          PyExpression defaultValue = p.getDefaultValue();
          if (defaultValue != null && i < argumentsSize) {
            if (arguments[i].getText().equals(defaultValue.getText())) {
              registerProblem(arguments[i], "Argument equals to default parameter value",
                              new RemoveArgumentEqualDefaultQuickFix());
            }
          }
        }
      }
    }
  }
}
