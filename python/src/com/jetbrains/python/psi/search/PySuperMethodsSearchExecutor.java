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
        PyFunction superMethod = superClass.findMethodByName(name, false, null);
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
            for (final PyFunction function : PyTypeUtil.getMembersOfType(classLikeType, PyFunction.class, true, context)) {
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
