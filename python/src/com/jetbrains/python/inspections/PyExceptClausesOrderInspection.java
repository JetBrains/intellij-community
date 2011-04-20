package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Alexey.Ivanov
 */
public class PyExceptClausesOrderInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.bad.except.clauses.order");
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
    public void visitPyTryExceptStatement(PyTryExceptStatement node) {
      PyExceptPart[] exceptParts = node.getExceptParts();
      if (exceptParts.length > 1) {
        Set<PyClass> exceptClasses = new HashSet<PyClass>();
        for (PyExceptPart exceptPart : exceptParts) {
          PyExpression exceptClass = exceptPart.getExceptClass();
          if (exceptClass instanceof PyReferenceExpression) {
            PsiElement element = ((PyReferenceExpression) exceptClass).followAssignmentsChain(myTypeEvalContext).getElement();
            if (element instanceof PyClass) {
              PyClass pyClass = (PyClass)element;
              if (exceptClasses.contains(pyClass)) {
                registerProblem(exceptClass, PyBundle.message("INSP.class.$0.already.caught", pyClass.getName()));
              } else {
                for (PyClass superClass: pyClass.getSuperClasses()) {
                  if (exceptClasses.contains(superClass)) {
                    registerProblem(exceptClass, PyBundle.message("INSP.class.$0.superclass.$1.already.caught", superClass.getName(), pyClass.getName()));
                  }
                }
              }
              exceptClasses.add(pyClass);
            }
          }
        }
      }
    }
  }
}
