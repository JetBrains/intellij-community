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
package com.jetbrains.python.findUsages;

import com.intellij.find.findUsages.FindUsagesHandlerBase;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.pyi.PyiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Important note: please update PyFindUsagesHandlerFactory#proxy on any changes here.
 */
public abstract class PyFindUsagesHandler extends FindUsagesHandlerBase {
  protected PyFindUsagesHandler(@NotNull PsiElement psiElement) {
    super(psiElement);
  }

  @NotNull
  @Override
  public FindUsagesOptions getFindUsagesOptions(@Nullable DataContext dataContext) {
    PyFindUsagesOptions sharedOpts = PyFindUsagesOptions.getInstance(getProject());
    return !isSearchForTextOccurrencesAvailable(getPsiElement(), false) ? (PyFindUsagesOptions)sharedOpts.clone() : sharedOpts;
  }

  @Override
  public boolean isSearchForTextOccurrencesAvailable(@NotNull PsiElement psiElement, boolean isSingleFile) {
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
    for (PsiElement element: result) {
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

  @Nullable
  protected static PsiElement tryGetStubElement(@Nullable PsiElement element) {
    if (!(element instanceof PyElement)) return null;
    PsiElement result = PyiUtil.getPythonStub((PyElement)element);
    return result != element ? result : null;
  }

  @Nullable
  protected static PsiElement tryGetOriginalElement(@Nullable PsiElement element) {
    if (!(element instanceof PyElement)) return null;
    PsiElement result = PyiUtil.getOriginalElement((PyElement)element);
    return result != element ? result : null;
  }
}
