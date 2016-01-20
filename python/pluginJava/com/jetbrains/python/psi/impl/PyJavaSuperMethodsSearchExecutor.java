/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

/**
 * @author yole
 */
public class PyJavaSuperMethodsSearchExecutor implements QueryExecutor<PsiElement, PySuperMethodsSearch.SearchParameters> {
  public boolean execute(@NotNull final PySuperMethodsSearch.SearchParameters queryParameters, @NotNull final Processor<PsiElement> consumer) {
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
