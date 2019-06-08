// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.psi.impl;

import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.sh.psi.ShSimpleCommand;
import com.intellij.sh.psi.ShString;
import org.jetbrains.annotations.NotNull;

public class ShPsiImplUtil {
  @NotNull
  static PsiReference[] getReferences(@NotNull ShString o) {
    return ReferenceProvidersRegistry.getReferencesFromProviders(o);
  }

  @NotNull
  static PsiReference[] getReferences(@NotNull ShSimpleCommand o) {
    return ReferenceProvidersRegistry.getReferencesFromProviders(o);
  }
}
