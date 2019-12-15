// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.codeInsight;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.sh.psi.ShFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ShFunctionResolver implements ResolveCache.Resolver {
  @Override
  @Nullable
  public PsiElement resolve(@NotNull PsiReference ref, boolean incompleteCode) {
    PsiElement refElement = ref.getElement();
    PsiFile file = refElement.getContainingFile();
    if (file instanceof ShFile) {
      return ((ShFile)file).findFunctions().get(refElement.getText());
    }
    return null;
  }
}
