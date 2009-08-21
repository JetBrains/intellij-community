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
      /* TODO: implement more sophisticated logic.
       E.g. foo(a, b, c) -> foo(*params) is a compatible override, while foo(a, b=1) -> foo(a, c=1) isn't,
       but current implementation thinks otherwise.
       */
      for (PsiElement psiElement : PySuperMethodsSearch.search(function)) {
        final PyParameter[] parameters = function.getParameterList().getParameters();
        if (psiElement instanceof PyFunction) {
          final PyParameter[] superFunctionParameters = ((PyFunction)psiElement).getParameterList().getParameters();
          if (parameters.length != superFunctionParameters.length) {
            registerProblem(function.getParameterList(), "Number of parameters does not match parameters of base method");
            return;
          }

          for (int i = 0; i < parameters.length; ++i) {
            if (parameters[i] instanceof PyNamedParameter && superFunctionParameters[i] instanceof PyNamedParameter) {
              final PyNamedParameter namedParameter = (PyNamedParameter)parameters[i];
              final PyNamedParameter namedSuperFunctionParameter = (PyNamedParameter)superFunctionParameters[i];
              if (namedParameter.isKeywordContainer() != namedSuperFunctionParameter.isKeywordContainer() ||
                  namedParameter.isPositionalContainer() != namedSuperFunctionParameter.isPositionalContainer()) {
                registerProblem(namedParameter, "Method signature does not match signature of base method");
              }
            }
          }
        }
      }
    }
  }
}
