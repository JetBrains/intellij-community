/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * User : ktisha
 */
public final class PythonBuiltinReferenceResolveProvider implements PyReferenceResolveProvider {

  @NotNull
  @Override
  public List<RatedResolveResult> resolveName(@NotNull PyQualifiedExpression element, @NotNull TypeEvalContext context) {
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
    if (builtinsFile != null) {
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
