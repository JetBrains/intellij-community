// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;


public class PyJavaSuperMethodsSearchExecutor implements QueryExecutor<PsiElement, PySuperMethodsSearch.SearchParameters> {
  @Override
  public boolean execute(@NotNull final PySuperMethodsSearch.SearchParameters queryParameters, @NotNull final Processor<? super PsiElement> consumer) {
    PyFunction func = queryParameters.getDerivedMethod();
    PyClass containingClass = func.getContainingClass();
    if (containingClass != null) {
      for (PyClassLikeType type : containingClass.getSuperClassTypes(TypeEvalContext.codeInsightFallback(containingClass.getProject()))) {
        if (type instanceof PyJavaClassType) {
          final PsiClass psiClass = ((PyJavaClassType)type).getPsiClass();
          PsiMethod[] methods = psiClass.findMethodsByName(func.getName(), true);
          // the Python method actually does override/implement all of Java super methods with the same name
          if (!ContainerUtil.process(methods, consumer)) return false;
        }
      }
    }
    return true;
  }
}
