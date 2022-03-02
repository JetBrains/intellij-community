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
package com.jetbrains.python.findUsages;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Important note: please update PyFindUsagesHandlerFactory#proxy on any changes here.
 */
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
  public boolean isSearchForTextOccurrencesAvailable(@NotNull PsiElement psiElement, boolean isSingleFile) {
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
