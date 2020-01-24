// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.codeInsight;

import com.intellij.openapi.util.Conditions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveState;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sh.psi.*;
import com.intellij.sh.psi.impl.ShLazyBlockImpl;
import com.intellij.sh.psi.impl.ShLazyDoBlockImpl;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static com.intellij.util.containers.ContainerUtil.getFirstItem;

class ShFunctionResolver implements ResolveCache.Resolver {
  @Override
  @Nullable
  public PsiElement resolve(@NotNull PsiReference ref, boolean incompleteCode) {
    PsiElement refElement = ref.getElement();
    PsiFile file = refElement.getContainingFile();
    if (!(file instanceof ShFile)) return null;

    ShFunctionDeclarationProcessor processor = new ShFunctionDeclarationProcessor();
    file.processDeclarations(processor, ResolveState.initial(), null, refElement);
    Map<PsiElement, MultiMap<String, ShFunctionName>> functionsDeclarationsWithScope = processor.getFunctionsDeclarationsWithScope();
    PsiElement parent = getScopeParent(refElement);
    PsiElement result = null;
    do {
      assert parent != null;

      MultiMap<String, ShFunctionName> functionNameMultiMap = functionsDeclarationsWithScope.get(parent);
      if (functionNameMultiMap == null) {
        parent = getScopeParent(parent);
        continue;
      }

      result = getFirstItem(functionNameMultiMap.get(refElement.getText()));
      parent = getScopeParent(parent);
    } while (parent != null && result == null);

    return result;
  }

  private static PsiElement getScopeParent(@NotNull PsiElement element) {
    return PsiTreeUtil.findFirstParent(element, true, Conditions.instanceOf(ShLazyDoBlockImpl.class, ShLazyBlockImpl.class, ShFile.class));
  }
}
