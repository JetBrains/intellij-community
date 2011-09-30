package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * Inspection to detect occurrences of new-style class features in old-style classes
 */
public class PyOldStyleClassesInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.oldstyle.class");
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
    public void visitPyClass(final PyClass node) {
      if (!node.isNewStyleClass()) {
        for (PyTargetExpression attr : node.getClassAttributes()) {
          if ("__slots__".equals(attr.getName())) {
            registerProblem(attr, "Old-style class contains __slots__ definition");
          }
        }
        for (PyFunction attr : node.getMethods()) {
          if ("__getattribute__".equals(attr.getName())) {
            registerProblem(attr, "Old-style class contains __getattribute__ definition");
          }
        }
      }
    }

    @Override
    public void visitPyCallExpression(final PyCallExpression node) {
      PyClass klass = PsiTreeUtil.getParentOfType(node, PyClass.class);
      if (klass != null && !klass.isNewStyleClass()) {
        PyExpression[] superClassExprs = klass.getSuperClassExpressions();
        PsiElement[] superClasses = klass.getSuperClassElements();
        if (superClasses.length != superClassExprs.length) return;
        if (PyUtil.isSuperCall(node))
          registerProblem(node.getCallee(), "Old-style class contains call for super method");
      }
    }
  }
}
