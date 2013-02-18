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
          overridingMethod = pyClass.findMethodByName(baseMethod.getName(), false);
          if (overridingMethod != null) {
            final Property baseProperty = baseMethod.getProperty();
            final Property overridingProperty = overridingMethod.getProperty();
            if (baseProperty != null && overridingProperty != null) {
              final AccessDirection direction = PyUtil.getPropertyAccessDirection(baseMethod);
              final Callable callable = overridingProperty.getByDirection(direction).valueOrNull();
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
