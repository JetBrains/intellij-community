// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.resolve;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.SmartList;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public final class PythonOverridingBuiltinReferenceResolveProvider implements PyOverridingReferenceResolveProvider {

  @Override
  public @NotNull List<RatedResolveResult> resolveName(@NotNull PyQualifiedExpression element, @NotNull TypeEvalContext context) {
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
