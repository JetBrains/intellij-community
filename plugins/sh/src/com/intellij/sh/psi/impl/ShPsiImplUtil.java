package com.intellij.sh.psi.impl;

import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.sh.psi.ShSimpleCommand;
import com.intellij.sh.psi.ShString;
import org.jetbrains.annotations.NotNull;

public class ShPsiImplUtil {
  @NotNull
  public static PsiReference[] getReferences(@NotNull ShString o) {
    return ReferenceProvidersRegistry.getReferencesFromProviders(o);
  }

  @NotNull
  public static PsiReference[] getReferences(@NotNull ShSimpleCommand o) {
    return ReferenceProvidersRegistry.getReferencesFromProviders(o);
  }
}
