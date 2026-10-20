// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.findUsages;

import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.usageView.UsageViewLongNameLocation;
import com.jetbrains.python.psi.PyElement;
import org.jetbrains.annotations.NotNull;


public final class PyElementDescriptionProvider implements ElementDescriptionProvider {
  @Override
  public String getElementDescription(@NotNull PsiElement element, @NotNull ElementDescriptionLocation location) {
    if (location instanceof UsageViewLongNameLocation) {
      if (element instanceof PsiNamedElement && element instanceof PyElement) {
        return ((PsiNamedElement)element).getName();
      }
    }
    return null;
  }
}
