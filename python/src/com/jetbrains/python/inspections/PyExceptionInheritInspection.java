package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   06.03.2010
 * Time:   19:39:09
 */
public class PyExceptionInheritInspection extends LocalInspectionTool {
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
    return PyBundle.message("INSP.NAME.exception.not.inherit");
  }

  @NotNull
  @Override
  public String getShortName() {
    return "PyExceptionInheritInspection";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new Visitor(holder);
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  private static class Visitor extends PyInspectionVisitor {

    public Visitor(final ProblemsHolder holder) {
      super(holder);
    }

    @Override
    public void visitPyRaiseStatement(PyRaiseStatement node) {
      PyExpression[] expressions = node.getExpressions();
      if (expressions == null) {
        return;
      }
      PyExpression expression = expressions[0];
      if (expression instanceof PyCallExpression) {
        PyExpression callee = ((PyCallExpression)expression).getCallee();
        if (callee instanceof PyReferenceExpression) {
          PsiElement psiElement = ((PyReferenceExpression)callee).getReference().resolve();
          if (psiElement instanceof PyClass) {
            for (PyClass pyClass : PyUtil.getAllSuperClasses((PyClass)psiElement)) {
              if ("Exception".equals(pyClass.getName())) {
                return;
              }
            }
            registerProblem(expression, "Exception doesn't inherit from base \'Exception\' class");
          }
        }
      }
    }
  }
}
