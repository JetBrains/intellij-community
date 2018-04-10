/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
                         @NotNull final Processor<PyFunction> consumer) {
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
