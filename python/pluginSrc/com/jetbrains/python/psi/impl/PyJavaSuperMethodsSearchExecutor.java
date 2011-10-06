package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyJavaSuperMethodsSearchExecutor implements QueryExecutor<PsiElement, PySuperMethodsSearch.SearchParameters> {
  public boolean execute(@NotNull final PySuperMethodsSearch.SearchParameters queryParameters, @NotNull final Processor<PsiElement> consumer) {
    PyFunction func = queryParameters.getDerivedMethod();
    PyClass containingClass = func.getContainingClass();
    if (containingClass != null) {
      PsiElement[] superClassElements = containingClass.getSuperClassElements();
      for(PsiElement element: superClassElements) {
        if (element instanceof PsiClass) {
          PsiClass psiClass = (PsiClass) element;
          PsiMethod[] methods = psiClass.findMethodsByName(func.getName(), true);
          // the Python method actually does override/implement all of Java super methods with the same name
          if (!ContainerUtil.process(methods, consumer)) return false;
        }
      }
    }
    return true;
  }
}
