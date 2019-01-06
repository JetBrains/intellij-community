package com.intellij.bash.psi.impl;

import com.intellij.bash.psi.BashSimpleCommand;
import com.intellij.bash.psi.BashString;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import org.jetbrains.annotations.NotNull;

public class BashPsiImplUtil {
  @NotNull
  public static PsiReference[] getReferences(@NotNull BashString o) {
    return ReferenceProvidersRegistry.getReferencesFromProviders(o);
  }

  @NotNull
  public static PsiReference[] getReferences(@NotNull BashSimpleCommand o) {
    return ReferenceProvidersRegistry.getReferencesFromProviders(o);
  }
}
