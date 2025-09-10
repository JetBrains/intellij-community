// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.backend.codeInsight;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.ResolveState;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sh.backend.codeInsight.processor.ShFunctionDeclarationProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShFunctionReference extends PsiReferenceBase<PsiElement> {
  public ShFunctionReference(@NotNull PsiElement element) {
    super(element, TextRange.create(0, element.getTextLength()));
  }

  @Override
  public @Nullable PsiElement resolve() {
    return CachedValuesManager.getCachedValue(myElement, new ShFunctionCachedValueProvider(myElement));
  }

  private static final class ShFunctionCachedValueProvider implements CachedValueProvider<PsiElement> {
    private final PsiElement myElement;

    private ShFunctionCachedValueProvider(PsiElement element) {myElement = element;}

    @Override
    public @Nullable Result<PsiElement> compute() {
      return CachedValueProvider.Result.create(resolveInner(), myElement.getContainingFile());
    }

    private @Nullable PsiElement resolveInner() {
      ShFunctionDeclarationProcessor functionProcessor = new ShFunctionDeclarationProcessor(myElement.getText());
      PsiTreeUtil.treeWalkUp(functionProcessor, myElement, myElement.getContainingFile(), ResolveState.initial());
      return functionProcessor.getFunction();
    }
  }
}