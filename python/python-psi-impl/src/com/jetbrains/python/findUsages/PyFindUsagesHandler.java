// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.findUsages;

import com.intellij.find.findUsages.FindUsagesHandlerBase;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.pyi.PyiUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Important note: please update PyFindUsagesHandlerFactory#proxy on any changes here.
 */
@ApiStatus.Internal
public abstract class PyFindUsagesHandler extends FindUsagesHandlerBase {
  protected PyFindUsagesHandler(@NotNull PsiElement psiElement) {
    super(psiElement);
  }

  @Override
  public @NotNull FindUsagesOptions getFindUsagesOptions(@Nullable DataContext dataContext) {
    PyFindUsagesOptions sharedOpts = PyFindUsagesOptions.getInstance(getProject());
    return !isSearchForTextOccurrencesAvailable(getPsiElement(), false) ? (PyFindUsagesOptions)sharedOpts.clone() : sharedOpts;
  }

  @Override
  protected boolean isSearchForTextOccurrencesAvailable(@NotNull PsiElement psiElement, boolean isSingleFile) {
    return super.isSearchForTextOccurrencesAvailable(psiElement, isSingleFile);
  }

  @Override
  public PsiElement @NotNull [] getPrimaryElements() {
    List<PsiElement> result = new ArrayList<>();
    result.add(myPsiElement);

    completePrimaryElementsWithStubAndOriginalElements(result);

    return result.toArray(PsiElement.EMPTY_ARRAY);
  }

  protected void completePrimaryElementsWithStubAndOriginalElements(@NotNull List<PsiElement> result) {
    List<PsiElement> additionalElements = new ArrayList<>();
    for (PsiElement element : result) {
      PsiElement stubElement = tryGetStubElement(element);
      if (stubElement != null) {
        additionalElements.add(stubElement);
      }

      PsiElement originalElement = tryGetOriginalElement(element);
      if (originalElement != null) {
        additionalElements.add(originalElement);
      }
    }
    result.addAll(additionalElements);
  }

  protected static @Nullable PsiElement tryGetStubElement(@Nullable PsiElement element) {
    if (!(element instanceof PyElement)) return null;
    PsiElement result = PyiUtil.getPythonStub((PyElement)element);
    return result != element ? result : null;
  }

  protected static @Nullable PsiElement tryGetOriginalElement(@Nullable PsiElement element) {
    if (!(element instanceof PyElement)) return null;
    PsiElement result = PyiUtil.getOriginalElement((PyElement)element);
    return result != element ? result : null;
  }
}
