// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.findUsages;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Important note: please update PyFindUsagesHandlerFactory#proxy on any changes here.
 */
@ApiStatus.Internal
public class PyClassFindUsagesHandler extends PyFindUsagesHandler {

  public PyClassFindUsagesHandler(@NotNull PyClass psiElement) {
    super(psiElement);
  }

  @Override
  protected boolean isSearchForTextOccurrencesAvailable(@NotNull PsiElement psiElement, boolean isSingleFile) {
    return true;
  }
}
