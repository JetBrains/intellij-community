package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Jul 28, 2009
 * Time: 4:06:18 PM
 */
public class PyMethodOverridingInspection extends LocalInspectionTool {

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.method.over");
  }

  @NotNull
  public String getShortName() {
    return "PyMethodOverridingInspection";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new Visitor(holder);
  }

  public static class Visitor extends PyInspectionVisitor {

    public Visitor(final ProblemsHolder holder) {
      super(holder);
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
