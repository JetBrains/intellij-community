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
package com.jetbrains.python.psi.impl;

import com.intellij.codeInsight.completion.CompletionUtilCoreImpl;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.references.KeywordArgumentCompletionUtil;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;


public class PyKeywordArgumentReference extends PsiReferenceBase.Poly<PyKeywordArgument> {
  public PyKeywordArgumentReference(@NotNull PyKeywordArgument element, TextRange textRange) {
    super(element, textRange, true);
  }

  @Override
  public Object @NotNull [] getVariants() {
    final PyKeywordArgument originalElement = CompletionUtilCoreImpl.getOriginalElement(myElement);
    if (originalElement != null) {
      final List<LookupElement> ret = new ArrayList<>();
      final TypeEvalContext evalCtx = TypeEvalContext.codeCompletion(originalElement.getProject(), originalElement.getContainingFile());
      KeywordArgumentCompletionUtil.collectFunctionArgNames(originalElement, ret, evalCtx, false);
      return ret.toArray();
    }

    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    final String keyword = myElement.getKeyword();
    if (keyword == null) {
      return ResolveResult.EMPTY_ARRAY;
    }
    PsiElement call = PsiTreeUtil.getParentOfType(myElement, PyCallExpression.class, PyClass.class);
    if (!(call instanceof PyCallExpression)) {
      return ResolveResult.EMPTY_ARRAY;
    }

    List<ResolveResult> resultList = new ArrayList<>();
    TypeEvalContext evalCtx = TypeEvalContext.codeInsightFallback(call.getProject());
    StreamEx.of(((PyCallExpression)call).multiResolveCallee(PyResolveContext.defaultContext(evalCtx)))
      .map(it -> it.getParameters(evalCtx))
      .nonNull()
      .flatMap(it -> StreamEx.of(it))
      .filter(it -> keyword.equals(it.getName()))
      .map(it -> it.getDeclarationElement())
      .nonNull()
      .forEach(it -> resultList.add(new PsiElementResolveResult(it)));

    return resultList.toArray(ResolveResult.EMPTY_ARRAY);
  }

  private static @Nullable PyFunction resolveToFunction(PsiElement element, HashSet<PsiElement> visited) {
    if (visited.contains(element)) {
      return null;
    }
    visited.add(element);
    if (element instanceof PyFunction) {
      return (PyFunction)element;
    }
    if (element instanceof PyTargetExpression) {
      final PyExpression assignedValue = ((PyTargetExpression)element).findAssignedValue();
      return resolveToFunction(assignedValue, visited);
    }
    if (element instanceof PyReferenceExpression) {
      final PsiElement resolveResult = ((PyReferenceExpression)element).getReference().resolve();
      return resolveToFunction(resolveResult, visited);
    }
    return null;
  }
}
