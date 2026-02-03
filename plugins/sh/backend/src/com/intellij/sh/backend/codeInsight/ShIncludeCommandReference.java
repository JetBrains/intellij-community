// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.sh.backend.codeInsight;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.sh.psi.ShSimpleCommandElement;
import com.intellij.sh.psi.impl.ShIncludeCommandImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ShIncludeCommandReference extends PsiReferenceBase<PsiElement> {
  public ShIncludeCommandReference(@NotNull PsiElement element) {
    super(element, TextRange.create(0, element.getTextLength()));
  }

  @Override
  public @Nullable PsiElement resolve() {
    return CachedValuesManager.getCachedValue(myElement, new ShIncludeCommandCachedValueProvider(myElement));
  }

  private static final class ShIncludeCommandCachedValueProvider implements CachedValueProvider<PsiElement> {
    private final PsiElement myElement;

    private ShIncludeCommandCachedValueProvider(PsiElement element) { myElement = element;}

    @Override
    public @Nullable Result<PsiElement> compute() {
      return Result.create(resolveInner(), myElement.getContainingFile());
    }

    private @Nullable PsiElement resolveInner() {
      PsiElement parent = myElement.getParent();
      if (!(parent instanceof ShIncludeCommandImpl includeCommand)) return null;
      List<ShSimpleCommandElement> commandList = includeCommand.getSimpleCommandElementList();
      if (commandList.size() <= 0 || commandList.get(0) != myElement) return null;
      return includeCommand.getReferencingFile(myElement);
    }
  }
}