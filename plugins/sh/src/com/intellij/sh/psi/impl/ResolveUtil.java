// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.sh.psi.ShCompositeElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ResolveUtil {
  static boolean processChildren(@NotNull PsiElement element,
                                 @NotNull PsiScopeProcessor processor,
                                 @NotNull ResolveState substitutor,
                                 @Nullable PsiElement lastParent,
                                 @NotNull PsiElement place) {
    PsiElement run = lastParent == null ? element.getLastChild() : lastParent.getPrevSibling();
    while (run != null) {
      if (run instanceof ShCompositeElement && !run.processDeclarations(processor, substitutor, null, place)) {
        return false;
      }
      run = run.getPrevSibling();
    }
    return true;
  }
}
