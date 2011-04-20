package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexey.Ivanov
 */
public class PySuperArgumentsInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.wrong.super.arguments");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  private static class Visitor extends PyInspectionVisitor {

    public Visitor(final ProblemsHolder holder, LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyCallExpression(PyCallExpression node) {
      final PyExpression callee = node.getCallee();
      if (callee != null) {
        if (PyNames.SUPER.equals(callee.getName())) {
          PyExpression[] arguments = node.getArguments();
          if (arguments.length == 2) {
            if (arguments[0] instanceof PyReferenceExpression && arguments[1] instanceof PyReferenceExpression) {
              PyClass firstClass = findClassOf(arguments[0]);
              PyClass secondClass = findClassOf(arguments[1]);
              if (firstClass != null && secondClass != null) {
                if (!secondClass.isSubclass(firstClass)) {
                  registerProblem(
                    node.getArgumentList(),
                    PyBundle.message("INSP.$0.is.not.superclass.of.$1",
                    secondClass.getName(), firstClass.getName())
                  );
                }
              }
            }
          }
        }
      }
    }

    @Nullable
    private PyClass findClassOf(PyExpression argument) {
      PsiElement firstElement = ((PyReferenceExpression)argument).followAssignmentsChain(myTypeEvalContext).getElement();
      PyClass firstClass = null;
      if (firstElement instanceof PyClass) firstClass = (PyClass)firstElement;
      else if (firstElement instanceof PyExpression) {
        PyType first_type = ((PyExpression)firstElement).getType(TypeEvalContext.fast());
        if (first_type instanceof PyClassType) {
          firstClass = ((PyClassType)first_type).getPyClass();
        }
      }
      return firstClass;
    }
  }
}
