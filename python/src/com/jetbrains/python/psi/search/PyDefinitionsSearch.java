// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.search;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.intellij.util.QueryExecutor;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyDefinitionsSearch implements QueryExecutor<PsiElement, PsiElement> {
  public boolean execute(@NotNull final PsiElement queryParameters, @NotNull final Processor<? super PsiElement> consumer) {
    if (queryParameters instanceof PyClass) {
      final Query<PyClass> query = PyClassInheritorsSearch.search((PyClass)queryParameters, true);
      return query.forEach(pyClass -> {
        return consumer.process(pyClass);
      });
    }
    else if (queryParameters instanceof PyFunction) {
      final Query<PyFunction> query =
        ReadAction.compute(() -> PyOverridingMethodsSearch.search((PyFunction)queryParameters, true));

      return query.forEach(
        pyFunction -> {
          return consumer.process(pyFunction);
        }
      );
    }
    else if (queryParameters instanceof PyTargetExpression) {  // PY-237
      final PsiElement parent = ReadAction.compute(() -> queryParameters.getParent());

      if (parent instanceof PyAssignmentStatement) {
        return consumer.process(parent);
      }
    }
    return true;
  }
}
