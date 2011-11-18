package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexey.Ivanov
 */
public class PyMethodOverridingInspection extends PyInspection {
  @Nls
  @NotNull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.method.over");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  public static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyFunction(final PyFunction function) {
      // sanity checks
      PyClass cls = function.getContainingClass();
      if (cls == null) return; // not a method, ignore
      String name = function.getName();
      if (PyNames.INIT.equals(name) || PyNames.NEW.equals(name)) return;  // these are expected to change signature
      // real work
      for (PsiElement psiElement : PySuperMethodsSearch.search(function)) {
        if (psiElement instanceof PyFunction) {
          if (! function.getParameterList().isCompatibleTo(((PyFunction)psiElement).getParameterList())) {
            registerProblem(function.getParameterList(), PyBundle.message("INSP.signature.mismatch"));
          }
        }
      }
    }
  }
}
