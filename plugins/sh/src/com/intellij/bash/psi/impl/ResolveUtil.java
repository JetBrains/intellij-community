package com.intellij.bash.psi.impl;

import com.intellij.bash.psi.ShCompositeElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ResolveUtil {
  public static boolean treeWalkUp(@Nullable PsiElement place, @NotNull PsiScopeProcessor processor) {
    PsiElement lastParent = null;
    PsiElement run = place;
    while (run != null) {
      if (place != run && !run.processDeclarations(processor, ResolveState.initial(), lastParent, place)) return false;
      lastParent = run;
      run = run.getParent();
    }
    return true;
  }

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
