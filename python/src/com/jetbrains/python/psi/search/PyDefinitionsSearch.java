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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
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
  public boolean execute(@NotNull final PsiElement queryParameters, @NotNull final Processor<PsiElement> consumer) {
    if (queryParameters instanceof PyClass) {
      final Query<PyClass> query = PyClassInheritorsSearch.search((PyClass)queryParameters, true);
      return query.forEach(new Processor<PyClass>() {
        public boolean process(final PyClass pyClass) {
          return consumer.process(pyClass);
        }
      });
    }
    else if (queryParameters instanceof PyFunction) {
      final Query<PyFunction> query = ApplicationManager.getApplication().runReadAction(
        new Computable<Query<PyFunction>>() {
          @Override
          public Query<PyFunction> compute() {
            return PyOverridingMethodsSearch.search((PyFunction)queryParameters, true);
          }
        }
      );

      return query.forEach(
        new Processor<PyFunction>() {
          public boolean process(final PyFunction pyFunction) {
            return consumer.process(pyFunction);
          }
        }
      );
    }
    else if (queryParameters instanceof PyTargetExpression) {  // PY-237
      final PsiElement parent = ApplicationManager.getApplication().runReadAction(
        new Computable<PsiElement>() {
          @Override
          public PsiElement compute() {
            return queryParameters.getParent();
          }
        }
      );

      if (parent instanceof PyAssignmentStatement) {
        return consumer.process(parent);
      }
    }
    return true;
  }
}
