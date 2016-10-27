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

import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.impl.references.PyReferenceImpl;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * User : ktisha
 */
public class PythonBuiltinReferenceResolveProvider implements PyReferenceResolveProvider {
  @NotNull
  @Override
  public List<RatedResolveResult> resolveName(@NotNull PyQualifiedExpression element) {
    final List<RatedResolveResult> result = new ArrayList<>();
    final PsiElement realContext = PyPsiUtils.getRealContext(element);
    final String referencedName = element.getReferencedName();
    final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(realContext);
    final TypeEvalContext typeEvalContext = TypeEvalContext.codeInsightFallback(element.getProject());

    // resolve to module __doc__
    if (PyNames.DOC.equals(referencedName)) {
      result.addAll(
        Optional
          .ofNullable(builtinCache.getObjectType())
          .map(type -> type.resolveMember(referencedName, element, AccessDirection.of(element),
                                          PyResolveContext.noImplicits().withTypeEvalContext(typeEvalContext)))
          .orElse(Collections.emptyList())
      );
    }

    // ...as a builtin symbol
    final PyFile bfile = builtinCache.getBuiltinsFile();
    if (bfile != null && !PyUtil.isClassPrivateName(referencedName)) {
      PsiElement resultElement = bfile.getElementNamed(referencedName);
      if (resultElement == null && "__builtins__".equals(referencedName)) {
        resultElement = bfile; // resolve __builtins__ reference
      }
      if (resultElement != null) {
        result.add(new ImportedResolveResult(resultElement, PyReferenceImpl.getRate(resultElement, typeEvalContext), null));
      }
    }
    return result;
  }
}
