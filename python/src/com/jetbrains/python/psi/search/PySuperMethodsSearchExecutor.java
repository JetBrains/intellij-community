package com.jetbrains.python.psi.search;

import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;

/**
 * @author yole
 */
public class PySuperMethodsSearchExecutor implements QueryExecutor<PsiElement, PySuperMethodsSearch.SearchParameters> {
  public boolean execute(final PySuperMethodsSearch.SearchParameters queryParameters, final Processor<PsiElement> consumer) {
    PyFunction func = queryParameters.getDerivedMethod();
    String name = func.getName();
    PyClass containingClass = func.getContainingClass();
    if (name != null && containingClass != null) {
      PyClass[] superClasses = containingClass.getSuperClasses();
      if (superClasses != null) {
        for(PyClass superClass: superClasses) {
          PyFunction superMethod = superClass.findMethodByName(name);
          if (superMethod != null) {
            if (!consumer.process(superMethod)) return false;
          }
        }
      }
    }
    return true;
  }
}
