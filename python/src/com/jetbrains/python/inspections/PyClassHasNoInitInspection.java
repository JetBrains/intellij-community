package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.inspections.quickfix.AddMethodQuickFix;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.types.PyClassTypeImpl;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: ktisha
 * See pylint W0232
 */
public class PyClassHasNoInitInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.class.has.no.init");
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
    public void visitPyClass(PyClass node) {
      final PyExpression[] classes = node.getSuperClassExpressions();
      for (PyExpression pyClass : classes) {
        final PsiReference reference = pyClass.getReference();
        if (reference == null || reference.resolve() == null) return;
        if (reference.getCanonicalText().contains(PyNames.TEST_CASE)) return;
      }
      final PyFunction init = node.findInitOrNew(true);
      if (init == null) {
        registerProblem(node.getNameIdentifier(), PyBundle.message("INSP.class.has.no.init"),
                        new AddMethodQuickFix("__init__", new PyClassTypeImpl(node, false), false));
      }
    }
  }
}