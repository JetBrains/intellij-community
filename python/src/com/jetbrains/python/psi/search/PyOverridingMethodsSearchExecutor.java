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
package com.jetbrains.python.psi.search;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyOverridingMethodsSearchExecutor implements QueryExecutor<PyFunction, PyOverridingMethodsSearch.SearchParameters> {
  public boolean execute(@NotNull final PyOverridingMethodsSearch.SearchParameters queryParameters, @NotNull final Processor<PyFunction> consumer) {
    final PyFunction baseMethod = queryParameters.getFunction();
    PyClass containingClass = baseMethod.getContainingClass();
    return PyClassInheritorsSearch.search(containingClass, queryParameters.isCheckDeep()).forEach(new Processor<PyClass>() {
      public boolean process(final PyClass pyClass) {
        final AccessToken accessToken = ApplicationManager.getApplication().acquireReadActionLock();
        PyFunction overridingMethod;
        try {
          overridingMethod = pyClass.findMethodByName(baseMethod.getName(), false, null);
          if (overridingMethod != null) {
            final Property baseProperty = baseMethod.getProperty();
            final Property overridingProperty = overridingMethod.getProperty();
            if (baseProperty != null && overridingProperty != null) {
              final AccessDirection direction = PyUtil.getPropertyAccessDirection(baseMethod);
              final PyCallable callable = overridingProperty.getByDirection(direction).valueOrNull();
              overridingMethod = (callable instanceof PyFunction) ? (PyFunction)callable : null;
            }
          }
        }
        finally {
          accessToken.finish();
        }
        //noinspection SimplifiableIfStatement
        if (overridingMethod != null) {
          return consumer.process(overridingMethod);
        }
        return true;
      }
    });
  }
}
