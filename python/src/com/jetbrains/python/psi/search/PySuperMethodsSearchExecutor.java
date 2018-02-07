// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.search;

import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PySuperMethodsSearchExecutor implements QueryExecutor<PsiElement, PySuperMethodsSearch.SearchParameters> {
  @Override
  public boolean execute(@NotNull final PySuperMethodsSearch.SearchParameters queryParameters,
                         @NotNull final Processor<PsiElement> consumer) {
    final PyFunction func = queryParameters.getDerivedMethod();
    final String name = func.getName();
    final PyClass containingClass = func.getContainingClass();
    final TypeEvalContext context = queryParameters.getContext();
    if (name != null && containingClass != null) {
      for (PyClass superClass : containingClass.getAncestorClasses(context)) {
        PyFunction superMethod = superClass.findMethodByName(name, false, context);

        if (superMethod != null) {
          final Property property = func.getProperty();
          final Property superProperty = superMethod.getProperty();
          if (property != null && superProperty != null) {
            final AccessDirection direction = PyUtil.getPropertyAccessDirection(func);
            final PyCallable callable = superProperty.getByDirection(direction).valueOrNull();
            superMethod = (callable instanceof PyFunction) ? (PyFunction)callable : null;
          }

          boolean consumerWantsMore = consumer.process(superMethod);
          if (!queryParameters.isDeepSearch() || !consumerWantsMore) {
            return false;
          }
        }

      }
    }
    return true;
  }
}
