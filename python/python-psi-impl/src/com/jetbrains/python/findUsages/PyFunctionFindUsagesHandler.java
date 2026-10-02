// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.findUsages;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Important note: please update PyFindUsagesHandlerFactory#proxy on any changes here.
 */
@ApiStatus.Internal
public class PyFunctionFindUsagesHandler extends PyFindUsagesHandler {
  private final List<PsiElement> myAllElements;

  public PyFunctionFindUsagesHandler(@NotNull PsiElement psiElement) {
    super(psiElement);
    myAllElements = null;
  }

  public PyFunctionFindUsagesHandler(@NotNull PsiElement psiElement, List<PsiElement> allElements) {
    super(psiElement);
    myAllElements = allElements;
  }

  @Override
  protected boolean isSearchForTextOccurrencesAvailable(@NotNull PsiElement psiElement, boolean isSingleFile) {
    return true;
  }

  @Override
  public PsiElement @NotNull [] getPrimaryElements() {
    List<PsiElement> result = new ArrayList<>();
    if (myAllElements != null) {
      result.addAll(myAllElements);
    }
    else {
      result.add(myPsiElement);
    }

    completePrimaryElementsWithStubAndOriginalElements(result);

    return result.toArray(PsiElement.EMPTY_ARRAY);
  }
}
