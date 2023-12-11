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
package com.jetbrains.python.psi.resolve;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.SmartList;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public final class PythonOverridingBuiltinReferenceResolveProvider implements PyOverridingReferenceResolveProvider {

  @NotNull
  @Override
  public List<RatedResolveResult> resolveName(@NotNull PyQualifiedExpression element, @NotNull TypeEvalContext context) {
    final String referencedName = element.getReferencedName();

    // resolve implicit __class__ inside method
    if (element instanceof PyReferenceExpression &&
        PyNames.__CLASS__.equals(referencedName) &&
        !LanguageLevel.forElement(element).isPython2()) {
      final PyFunction containingFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class);

      if (containingFunction != null) {
        final PyClass containingClass = containingFunction.getContainingClass();

        if (containingClass != null) {
          final PyResolveProcessor processor = new PyResolveProcessor(referencedName);
          PyResolveUtil.scopeCrawlUp(processor, element, referencedName, containingFunction);

          if (processor.getElements().isEmpty()) {
            final PyType objectType = PyBuiltinCache.getInstance(element).getObjectType();
            if (objectType != null) {
              final PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
              final List<? extends RatedResolveResult> results =
                objectType.resolveMember(PyNames.__CLASS__, element, AccessDirection.of(element), resolveContext);
              if (results != null) {
                return new SmartList<>(results);
              }
            }
          }
        }
      }
    }

    return Collections.emptyList();
  }
}
