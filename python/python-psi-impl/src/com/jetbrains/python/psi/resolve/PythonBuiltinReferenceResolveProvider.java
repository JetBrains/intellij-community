// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.resolve;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * User : ktisha
 */
@ApiStatus.Internal
public final class PythonBuiltinReferenceResolveProvider implements PyReferenceResolveProvider {

  @Override
  public @NotNull List<RatedResolveResult> resolveName(@NotNull PyQualifiedExpression element, @NotNull TypeEvalContext context) {
    final String referencedName = element.getReferencedName();
    if (referencedName == null) {
      return Collections.emptyList();
    }

    final List<RatedResolveResult> result = new ArrayList<>();
    final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(PyPsiUtils.getRealContext(element));

    // resolve to module __doc__
    if (PyNames.DOC.equals(referencedName)) {
      result.addAll(
        Optional
          .ofNullable(builtinCache.getObjectType())
          .map(type -> type.resolveMember(referencedName, element, AccessDirection.of(element), PyResolveContext.defaultContext(context)))
          .orElse(Collections.emptyList())
      );
    }

    // ...as a builtin symbol
    final PyFile builtinsFile = builtinCache.getBuiltinsFile();
    if (builtinsFile != null && !PyUtil.isClassPrivateName(referencedName) && PyUtil.getInitialUnderscores(referencedName) != 1) {
      for (RatedResolveResult resolveResult : builtinsFile.multiResolveName(referencedName)) {
        result.add(new ImportedResolveResult(resolveResult.getElement(), resolveResult.getRate(), null));
      }
    }

    if (!element.isQualified() && "__builtins__".equals(referencedName)) {
      result.add(new ImportedResolveResult(PyBuiltinCache.getInstance(element).getBuiltinsFile(), RatedResolveResult.RATE_NORMAL, null));
    }

    return result;
  }
}
