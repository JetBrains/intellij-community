package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
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
    private static boolean isHavePositionalContainer(@NotNull PyParameter[] parameters) {
      for (PyParameter parameter: parameters) {
        if (parameter instanceof PyNamedParameter && ((PyNamedParameter) parameter).isPositionalContainer()) {
          return true;
        }
      }
      return false;
    }

    private static boolean isHaveKeywordContainer(@NotNull PyParameter[] parameters) {
      for (PyParameter parameter: parameters) {
        if (parameter instanceof PyNamedParameter && ((PyNamedParameter) parameter).isKeywordContainer()) {
          return true;
        }
      }
      return false;
    }

    public Visitor(final ProblemsHolder holder) {
      super(holder);
    }

    @Override
    public void visitPyFunction(final PyFunction function) {
      // sanity checks
      PyClass cls = function.getContainingClass();
      if (cls == null) return; // not a method, ignore
      String name = function.getName();
      if (PyNames.INIT.equals(name)) return; // inits are expected to change signature
      if (PyNames.NEW.equals(name)) return;  // __new__ is also expected to change signature
      // real work
      final PyParameter[] parameters = function.getParameterList().getParameters();
      boolean havePositionalContainer = isHavePositionalContainer(parameters);
      boolean haveKeywordContainer = isHaveKeywordContainer(parameters);
      for (PsiElement psiElement : PySuperMethodsSearch.search(function)) {
        if (psiElement instanceof PyFunction) {
          final PyParameter[] superFunctionParameters = ((PyFunction)psiElement).getParameterList().getParameters();
          boolean superHavePositionalContainer = isHavePositionalContainer(superFunctionParameters);
          boolean superHaveKeywordContainer = isHaveKeywordContainer(superFunctionParameters);
          if (parameters.length == superFunctionParameters.length) {
            if (havePositionalContainer == superHavePositionalContainer && haveKeywordContainer == superHaveKeywordContainer) {
              return;
            }
          }
          if (havePositionalContainer && parameters.length - 1 <= superFunctionParameters.length) {
            if (haveKeywordContainer == superHaveKeywordContainer) {
              return;
            }
          }
          registerProblem(function.getParameterList(), "Method signature does not match signature of base method");
        }
      }
    }
  }
}
