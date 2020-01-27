// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.codeInsight;

import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sh.psi.ShFile;
import com.intellij.sh.psi.ShFunctionName;
import com.intellij.sh.psi.impl.ShLazyBlockImpl;
import com.intellij.sh.psi.impl.ShLazyDoBlockImpl;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

class ShFunctionResolver implements ResolveCache.Resolver {
  @Override
  @Nullable
  public PsiElement resolve(@NotNull PsiReference ref, boolean incompleteCode) {
    PsiElement refElement = ref.getElement();
    PsiFile file = refElement.getContainingFile();
    if (!(file instanceof ShFile)) return null;
    return findFunctionDeclarationInScope((ShFile) file, refElement);
  }

  @Nullable
  private static PsiElement findFunctionDeclarationInScope(@NotNull ShFile shFile, @NotNull PsiElement refElement) {
    Map<PsiElement, MultiMap<String, ShFunctionName>> functionsDeclarationWithScope = shFile.getFunctionsDeclarationWithScope();
    PsiElement parent = getParentScope(refElement);
    TextRange elementTextRange = refElement.getTextRange();
    PsiElement result = null;
    // Search for suitable function declaration in parent scopes
    do {
      assert parent != null;

      MultiMap<String, ShFunctionName> functionNameMultiMap = functionsDeclarationWithScope.get(parent);
      if (functionNameMultiMap == null) {
        parent = getParentScope(parent);
        continue;
      }

      result = getNearestFunction(elementTextRange, functionNameMultiMap.get(refElement.getText()));
      parent = getParentScope(parent);
    } while (parent != null && result == null);
    return result;
  }

  @Nullable
  private static ShFunctionName getNearestFunction(@NotNull TextRange elementTextRange, @NotNull Collection<ShFunctionName> functions) {
    if (functions.isEmpty()) return null;
    ShFunctionName result = null;
    for (ShFunctionName function : functions) {
      if (isNearestFunction(elementTextRange, function, result) || isInsideFunction(elementTextRange, function)) {
        result = function;
      }
    }
    return result;
  }

  private static boolean isNearestFunction(@NotNull TextRange elementTextRange, @NotNull ShFunctionName function,
                                           @Nullable ShFunctionName previousResult) {
    TextRange functionTextRange = function.getTextRange();
    return functionTextRange.getEndOffset() < elementTextRange.getStartOffset()
    && (previousResult == null || previousResult.getTextRange().getEndOffset() < functionTextRange.getEndOffset());
  }

  private static boolean isInsideFunction(@NotNull TextRange elementTextRange, @NotNull ShFunctionName function) {
    return function.getTextRange().contains(elementTextRange);
  }

  private static PsiElement getParentScope(@NotNull PsiElement element) {
    return PsiTreeUtil.findFirstParent(element, true, Conditions.instanceOf(ShLazyDoBlockImpl.class, ShLazyBlockImpl.class, ShFile.class));
  }
}
