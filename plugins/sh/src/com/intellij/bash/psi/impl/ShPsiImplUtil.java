package com.intellij.bash.psi.impl;

import com.intellij.bash.psi.ShSimpleCommand;
import com.intellij.bash.psi.ShString;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
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
