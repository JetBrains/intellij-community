// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.search;

import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

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
    final Set<PyClass> foundMethodContainingClasses = new HashSet<>();
    final TypeEvalContext context = queryParameters.getContext();
    if (name != null && containingClass != null) {
      for (PyClass superClass : containingClass.getAncestorClasses(context)) {
        if (!queryParameters.isDeepSearch()) {
          boolean isAlreadyFound = false;
          for (PyClass alreadyFound : foundMethodContainingClasses) {
            if (alreadyFound.isSubclass(superClass, context)) {
              isAlreadyFound = true;
            }
          }
          if (isAlreadyFound) {
            continue;
          }
        }
        PyFunction superMethod = superClass.findMethodByName(name, false, context);
        if (superMethod != null) {
          final Property property = func.getProperty();
          final Property superProperty = superMethod.getProperty();
          if (property != null && superProperty != null) {
            final AccessDirection direction = PyUtil.getPropertyAccessDirection(func);
            final PyCallable callable = superProperty.getByDirection(direction).valueOrNull();
            superMethod = (callable instanceof PyFunction) ? (PyFunction)callable : null;
          }
        }


        if (superMethod == null && context != null) {
          // If super method still not found and we have context, we may use it to find method
          final PyClassLikeType classLikeType = PyUtil.as(context.getType(superClass), PyClassLikeType.class);
          if (classLikeType != null) {
            for (final PyFunction function : PyTypeUtil.getMembersOfType(classLikeType.toInstance(), PyFunction.class, true, context)) {
              final String elemName = function.getName();
              if (elemName != null && elemName.equals(func.getName())) {
                consumer.process(function);
              }
            }
          }
        }
        if (superMethod != null) {
          foundMethodContainingClasses.add(superClass);
          if (!consumer.process(superMethod)) {
            return false;
          }
        }
      }
    }
    return true;
  }
}
