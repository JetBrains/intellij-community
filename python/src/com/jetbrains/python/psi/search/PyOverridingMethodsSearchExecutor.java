// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.search;

import com.intellij.openapi.application.ReadAction;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyOverridingMethodsSearchExecutor implements QueryExecutor<PyFunction, PyOverridingMethodsSearch.SearchParameters> {
  public boolean execute(@NotNull final PyOverridingMethodsSearch.SearchParameters queryParameters,
                         @NotNull final Processor<? super PyFunction> consumer) {
    final PyFunction baseMethod = queryParameters.getFunction();

    final PyClass containingClass = ReadAction.compute(() -> baseMethod.getContainingClass());

    return PyClassInheritorsSearch.search(containingClass, queryParameters.isCheckDeep()).forEach(pyClass -> {
      PyFunction overridingMethod
        = ReadAction.compute(() -> {
        PyFunction o = pyClass.findMethodByName(baseMethod.getName(), false, null);
        if (o != null) {
          final Property baseProperty = baseMethod.getProperty();
          final Property overridingProperty = o.getProperty();
          if (baseProperty != null && overridingProperty != null) {
            final AccessDirection direction = PyUtil.getPropertyAccessDirection(baseMethod);
            final PyCallable callable = overridingProperty.getByDirection(direction).valueOrNull();
            o = (callable instanceof PyFunction) ? (PyFunction)callable : null;
          }
        }
        return o;
      });
      //noinspection SimplifiableIfStatement
      if (overridingMethod != null) {
        return consumer.process(overridingMethod);
      }
      return true;
    });
  }
}
