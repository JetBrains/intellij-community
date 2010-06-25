package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

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
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new Visitor(holder);
  }

  private static class Visitor extends PyInspectionVisitor {

    public Visitor(final ProblemsHolder holder) {
      super(holder);
    }

    @Override
    public void visitPyCallExpression(PyCallExpression node) {
      if ("super".equals(node.getCallee().getName())) {
        PyExpression[] arguments = node.getArguments();
        if (arguments.length == 2) {
          if (arguments[0] instanceof PyReferenceExpression && arguments[1] instanceof PyReferenceExpression) {
            PsiElement firstElement = ((PyReferenceExpression) arguments[0]).followAssignmentsChain().getElement();
            PsiElement secondElement = ((PyReferenceExpression) arguments[1]).followAssignmentsChain().getElement();
            if (firstElement instanceof PyClass && secondElement instanceof PyClass) {
              PyClass firstClass = (PyClass) firstElement;
              PyClass secondClass = (PyClass) secondElement;
              if (!secondClass.isSubclass(firstClass)) {
                registerProblem(node.getArgumentList(), PyBundle.message("INSP.$0.is.not.superclass.of.$1",
                                                                         secondClass.getName(), firstClass.getName()));
              }
            }
          }
        }
      }
    }
  }
}
